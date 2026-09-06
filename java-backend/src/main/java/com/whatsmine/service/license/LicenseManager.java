package com.whatsmine.service.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Integration with the Botble License Manager external API
 * (https://docs.botble.com/license-manager/), porting
 * php/app/Services/License/LicenseManager.php. Disabled by default
 * (license.verify=false) — this only does anything when an operator
 * distributing their own build of this app opts in with real credentials.
 * The activated license token is stored in a local file (storage/app/.license);
 * verification results are cached in memory so the server isn't hit on every
 * request, and a short grace window keeps a legitimately-licensed app
 * running if the license server is briefly down.
 *
 * NOT ported: downloading and applying a self-update package. PHP's Updater
 * overwrites application files on disk and re-runs migrations, which is a
 * reasonable model for a PHP deployment; blindly downloading and applying an
 * update package to a running compiled JVM process is a fundamentally
 * different (and far riskier) operation, so checkUpdate() is real but
 * apply-update honestly reports it isn't supported for this build rather
 * than fabricating an install.
 */
@Service
public class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);
    public static final List<String> TYPES = List.of("envato", "non_envato", "gumroad");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${license.verify:false}")
    private boolean verifyEnabled;

    @Value("${license.server-url:}")
    private String serverUrl;

    @Value("${license.api-key:}")
    private String apiKey;

    @Value("${license.product-id:}")
    private String productId;

    @Value("${license.verify-type:non_envato}")
    private String configuredVerifyType;

    @Value("${license.cache-hours:12}")
    private int cacheHours;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    @Value("${app.version:1.0.0}")
    private String currentVersion;

    private final ObjectMapper objectMapper;

    private volatile Instant cacheValidUntil;

    public LicenseManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Licensing is active only when fully configured and not switched off. */
    public boolean enabled() {
        return verifyEnabled && notBlank(productId) && notBlank(apiKey) && notBlank(serverUrl);
    }

    public String verifyType() {
        return TYPES.contains(configuredVerifyType) ? configuredVerifyType : "non_envato";
    }

    /** Only one type is offered unless the operator configures more via license.verify-type. */
    public List<String> verifyTypes() {
        return List.of(verifyType());
    }

    public String defaultVerifyType() {
        return verifyType();
    }

    public boolean isEnvato() {
        return "envato".equals(verifyType());
    }

    public String activatedType() {
        String type = readFile(typePath());
        return (type != null && TYPES.contains(type)) ? type : defaultVerifyType();
    }

    public boolean isActivated() {
        if (!enabled()) return true;
        return licenseData() != null;
    }

    public String licenseData() {
        return readFile(licensePath());
    }

    /** The activated purchase/license code with the middle masked, for display. */
    public String maskedCode() {
        String code = readFile(codePath());
        if (code == null) return null;

        int len = code.length();
        if (len <= 8) {
            return "•".repeat(Math.max(1, len - 2)) + code.substring(Math.max(0, len - 2));
        }
        return code.substring(0, 4) + "••••••••" + code.substring(len - 4);
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String productId() {
        return productId;
    }

    // ── API calls ────────────────────────────────────────────────────────

    public Map<String, Object> connectionCheck() {
        try {
            JsonNode res = httpGet("/api/external/connection-check");
            boolean ok = res != null && (boolTrue(res, "is_active") || boolTrue(res, "status"));
            return Map.of("ok", ok, "message", textOr(res, "message", ok ? "Connection successful." : "Connection failed."));
        } catch (Exception e) {
            return Map.of("ok", false, "message", "Couldn't reach the license server.");
        }
    }

    public Map<String, Object> activate(String licenseCode, String clientName, String verifyTypeParam) {
        licenseCode = licenseCode != null ? licenseCode.trim() : "";
        if (licenseCode.isEmpty()) {
            return Map.of("ok", false, "message", "Enter your license code.");
        }
        if (!enabled()) {
            return Map.of("ok", true, "message", "License verification is disabled.");
        }

        String type = (verifyTypeParam != null && TYPES.contains(verifyTypeParam)) ? verifyTypeParam : verifyType();

        JsonNode res;
        try {
            res = httpPost("/api/external/license/activate", Map.of(
                    "product_id", productId,
                    "license_code", licenseCode,
                    "client_name", clientName != null ? clientName : "",
                    "verify_type", type
            ));
        } catch (Exception e) {
            return Map.of("ok", false, "message", "Couldn't reach the license server. Check the server's internet connection and try again.");
        }

        if (res == null || !boolTrue(res, "is_active")) {
            return Map.of("ok", false, "message", textOr(res, "message", "License activation failed."));
        }

        String licenseDataValue = textOrNull(res, "lic_response");
        if (licenseDataValue == null) {
            licenseDataValue = textOrNull(res.path("data"), "license_data");
        }
        if (licenseDataValue == null || licenseDataValue.isBlank()) {
            return Map.of("ok", false, "message", "The license server did not return license data. Please try again.");
        }

        writeFile(licensePath(), licenseDataValue.trim());
        writeFile(codePath(), licenseCode);
        writeFile(typePath(), type);
        cacheValid();

        return Map.of("ok", true, "message", textOr(res, "message", "License activated."));
    }

    public Map<String, Object> verify(boolean useCache) {
        if (!enabled()) {
            return Map.of("ok", true, "message", "License verification is disabled.");
        }

        String data = licenseData();
        if (data == null) {
            return Map.of("ok", false, "message", "No license found. Please activate your license.", "needs_activation", true);
        }

        if (useCache && cacheValidUntil != null && Instant.now().isBefore(cacheValidUntil)) {
            return Map.of("ok", true, "message", "License is valid.");
        }

        JsonNode res;
        try {
            res = httpPost("/api/external/license/verify", Map.of("product_id", productId, "license_data", data));
        } catch (Exception e) {
            log.warn("License verify: server unreachable, granting grace window. {}", e.getMessage());
            cacheValidUntil = Instant.now().plus(Duration.ofHours(Math.min(6, Math.max(1, cacheHours))));
            return Map.of("ok", true, "message", "License server unreachable; using cached grace.");
        }

        if (res != null && boolTrue(res, "is_active")) {
            cacheValid();
            return Map.of("ok", true, "message", textOr(res, "message", "License is valid."));
        }

        cacheValidUntil = null;
        return Map.of("ok", false, "message", textOr(res, "message", "License is invalid."), "needs_activation", true);
    }

    public Map<String, Object> deactivate() {
        String data = licenseData();
        if (!enabled() || data == null) {
            clearLicenseData();
            return Map.of("ok", true, "message", "License cleared.");
        }

        try {
            JsonNode res = httpPost("/api/external/license/deactivate", Map.of("product_id", productId, "license_data", data));
            boolean ok = res != null && boolTrue(res, "is_active");
            clearLicenseData();
            return Map.of("ok", ok, "message", textOr(res, "message", ok ? "License deactivated." : "Deactivation failed on the server, but the local license was cleared."));
        } catch (Exception e) {
            clearLicenseData();
            return Map.of("ok", false, "message", "Couldn't reach the license server, but the local license was cleared.");
        }
    }

    public Map<String, Object> checkUpdate() {
        Map<String, Object> base = new java.util.LinkedHashMap<>();
        base.put("ok", false);
        base.put("update_available", false);
        base.put("current_version", currentVersion);
        base.put("version", null);
        base.put("released_at", null);
        base.put("summary", null);
        base.put("changelog", null);
        base.put("update_id", null);
        base.put("has_sql", false);
        base.put("message", "");

        if (!enabled()) {
            base.put("message", "Updates are not configured.");
            return base;
        }

        JsonNode res;
        try {
            res = httpPost("/api/external/update/check", Map.of("product_id", productId, "current_version", currentVersion));
        } catch (Exception e) {
            base.put("message", "Couldn't reach the update server.");
            return base;
        }

        if (res == null) {
            base.put("message", "Update check failed.");
            return base;
        }

        base.put("ok", true);
        base.put("update_available", boolTrue(res, "update_available"));
        base.put("version", textOrNull(res, "version"));
        base.put("released_at", firstNonNull(textOrNull(res, "release_date"), textOrNull(res, "released_at")));
        base.put("summary", textOrNull(res, "summary"));
        base.put("changelog", textOrNull(res, "changelog"));
        base.put("update_id", textOrNull(res, "update_id"));
        base.put("has_sql", boolTrue(res, "has_sql"));
        base.put("message", textOr(res, "message", ""));
        return base;
    }

    /** Honest limitation — see class Javadoc. */
    public Map<String, Object> applyUpdateUnsupported() {
        return Map.of("ok", false, "message",
                "Automatic update installation isn't supported for this build. Download the new release and redeploy manually.");
    }

    // ── Internals ────────────────────────────────────────────────────────

    private JsonNode httpGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .timeout(Duration.ofSeconds(20))
                .headers(headersArray())
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body() != null && !response.body().isBlank() ? objectMapper.readTree(response.body()) : null;
    }

    private JsonNode httpPost(String path, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .timeout(Duration.ofSeconds(20))
                .headers(headersArray())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body() != null && !response.body().isBlank() ? objectMapper.readTree(response.body()) : null;
    }

    private String[] headersArray() {
        return new String[]{
                "X-API-KEY", apiKey != null ? apiKey : "",
                "X-API-URL", appUrl != null ? appUrl : "",
                "X-API-IP", serverIp(),
                "X-API-LANGUAGE", "en",
                "Accept", "application/json"
        };
    }

    private String serverIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private String url(String path) {
        String base = serverUrl != null ? serverUrl.replaceAll("/+$", "") : "";
        return base + path;
    }

    private void cacheValid() {
        cacheValidUntil = Instant.now().plus(Duration.ofHours(Math.max(1, cacheHours)));
    }

    private void clearLicenseData() {
        deleteFile(licensePath());
        deleteFile(codePath());
        deleteFile(typePath());
        cacheValidUntil = null;
    }

    private Path licensePath() {
        return Paths.get("storage/app/.license").toAbsolutePath();
    }

    private Path codePath() {
        return Paths.get("storage/app/.license_code").toAbsolutePath();
    }

    private Path typePath() {
        return Paths.get("storage/app/.license_type").toAbsolutePath();
    }

    private String readFile(Path path) {
        try {
            if (!Files.exists(path)) return null;
            String data = Files.readString(path, StandardCharsets.UTF_8).trim();
            return data.isEmpty() ? null : data;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content.trim(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write license file {}: {}", path, e.getMessage());
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) { }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean boolTrue(JsonNode node, String field) {
        return node != null && node.path(field).asBoolean(false);
    }

    private String textOr(JsonNode node, String field, String fallback) {
        String v = textOrNull(node, field);
        return v != null ? v : fallback;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
