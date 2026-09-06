package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.service.IntegrationCredentialsService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized OAuth for all social networks, porting PHP's
 * Social\Services\OAuth\OAuthManager. Facebook/Instagram (page/post
 * publishing, distinct from the WhatsApp/Messenger/Instagram-DM webhook
 * work done earlier this session) reuse the existing system-wide "meta_app"
 * credentials (one Meta App serves all of these); LinkedIn/Twitter/YouTube/
 * TikTok each have their own oauth_* system credential slot (Admin >
 * Integrations), resolved via IntegrationCredentialsService.
 */
@Service
public class SocialOAuthManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final IntegrationCredentialsService credentialsService;
    private final ObjectMapper objectMapper;

    public SocialOAuthManager(IntegrationCredentialsService credentialsService, ObjectMapper objectMapper) {
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    public String generateState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public String generatePkceVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String pkceChallenge(String verifier) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Throws IllegalStateException if the network's OAuth app isn't configured. */
    public String getAuthUrl(String network, String state, String pkceVerifier, String callbackUrl) {
        return switch (network) {
            case "facebook" -> facebookAuthUrl(state, callbackUrl, "pages_manage_posts,pages_read_engagement,pages_show_list");
            case "instagram" -> facebookAuthUrl(state, callbackUrl, "instagram_basic,instagram_content_publish,pages_show_list");
            case "linkedin" -> linkedinAuthUrl(state, callbackUrl);
            case "twitter" -> twitterAuthUrl(state, pkceVerifier, callbackUrl);
            case "youtube" -> googleAuthUrl(state, callbackUrl);
            case "tiktok" -> tiktokAuthUrl(state, callbackUrl);
            default -> throw new IllegalArgumentException("Unsupported network: " + network);
        };
    }

    /** Returns {access_token, refresh_token?, expires_in?}. Throws if the app isn't configured or the exchange fails. */
    public Map<String, Object> exchangeCode(String network, String code, String callbackUrl, String pkceVerifier) throws Exception {
        return switch (network) {
            case "facebook", "instagram" -> facebookExchange(code, callbackUrl);
            case "linkedin" -> linkedinExchange(code, callbackUrl);
            case "twitter" -> twitterExchange(code, callbackUrl, pkceVerifier);
            case "youtube" -> googleExchange(code, callbackUrl);
            case "tiktok" -> tiktokExchange(code, callbackUrl);
            default -> throw new IllegalArgumentException("Unsupported network: " + network);
        };
    }

    /** Returns {access_token, refresh_token?, expires_in?}. Facebook/Instagram tokens are long-lived (no refresh). */
    public Map<String, Object> refresh(String network, String refreshToken) throws Exception {
        return switch (network) {
            case "twitter" -> twitterRefresh(refreshToken);
            case "youtube" -> googleRefresh(refreshToken);
            case "tiktok" -> tiktokRefresh(refreshToken);
            case "linkedin" -> linkedinRefresh(refreshToken);
            default -> throw new IllegalStateException(network + " tokens are long-lived; refresh isn't applicable.");
        };
    }

    // ── Facebook / Instagram (meta_app) ─────────────────────────────────

    private String facebookAuthUrl(String state, String redirect, String scopes) {
        String clientId = credentialsService.getCredential("meta_app", "app_id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Meta App is not configured.");
        }
        return "https://www.facebook.com/v19.0/dialog/oauth?"
                + "client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirect)
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&response_type=code";
    }

    private Map<String, Object> facebookExchange(String code, String redirect) throws Exception {
        String clientId = credentialsService.getCredential("meta_app", "app_id");
        String clientSecret = credentialsService.getCredential("meta_app", "app_secret");
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("Meta App is not configured.");
        }
        String url = "https://graph.facebook.com/v19.0/oauth/access_token"
                + "?client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirect)
                + "&code=" + enc(code);
        JsonNode res = get(url);
        return tokenResult(res, "access_token", null, "expires_in");
    }

    // ── LinkedIn ─────────────────────────────────────────────────────────

    private String linkedinAuthUrl(String state, String redirect) {
        String clientId = credentialsService.getCredential("oauth_linkedin", "client_id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("LinkedIn OAuth is not configured.");
        }
        return "https://www.linkedin.com/oauth/v2/authorization?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirect)
                + "&scope=" + enc("openid profile email w_member_social")
                + "&state=" + enc(state);
    }

    private Map<String, Object> linkedinExchange(String code, String redirect) throws Exception {
        String clientId = credentialsService.getCredential("oauth_linkedin", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_linkedin", "client_secret");
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("LinkedIn OAuth is not configured.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirect);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        JsonNode res = postForm("https://www.linkedin.com/oauth/v2/accessToken", form, null);
        return tokenResult(res, "access_token", null, "expires_in");
    }

    private Map<String, Object> linkedinRefresh(String refreshToken) throws Exception {
        String clientId = credentialsService.getCredential("oauth_linkedin", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_linkedin", "client_secret");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId != null ? clientId : "");
        form.put("client_secret", clientSecret != null ? clientSecret : "");
        JsonNode res = postForm("https://www.linkedin.com/oauth/v2/accessToken", form, null);
        Map<String, Object> result = tokenResult(res, "access_token", "refresh_token", "expires_in");
        if (result.get("access_token") == null) {
            throw new IllegalStateException("LinkedIn token refresh failed: " + res);
        }
        return result;
    }

    // ── Twitter / X (OAuth2 PKCE) ────────────────────────────────────────

    private String twitterAuthUrl(String state, String verifier, String redirect) {
        String clientId = credentialsService.getCredential("oauth_twitter", "client_id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Twitter/X OAuth is not configured.");
        }
        return "https://twitter.com/i/oauth2/authorize?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirect)
                + "&scope=" + enc("tweet.read tweet.write users.read offline.access")
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(pkceChallenge(verifier))
                + "&code_challenge_method=S256";
    }

    private Map<String, Object> twitterExchange(String code, String redirect, String verifier) throws Exception {
        String clientId = credentialsService.getCredential("oauth_twitter", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_twitter", "client_secret");
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("Twitter/X OAuth is not configured.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirect);
        form.put("code_verifier", verifier != null ? verifier : "");
        JsonNode res = postForm("https://api.twitter.com/2/oauth2/token", form, basicAuth(clientId, clientSecret));
        return tokenResult(res, "access_token", "refresh_token", "expires_in");
    }

    private Map<String, Object> twitterRefresh(String refreshToken) throws Exception {
        String clientId = credentialsService.getCredential("oauth_twitter", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_twitter", "client_secret");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        JsonNode res = postForm("https://api.twitter.com/2/oauth2/token", form, basicAuth(clientId, clientSecret));
        Map<String, Object> result = tokenResult(res, "access_token", "refresh_token", "expires_in");
        if (result.get("access_token") == null) {
            throw new IllegalStateException("Twitter token refresh failed: " + res);
        }
        return result;
    }

    // ── Google / YouTube ─────────────────────────────────────────────────

    private String googleAuthUrl(String state, String redirect) {
        String clientId = credentialsService.getCredential("oauth_youtube", "client_id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("YouTube/Google OAuth is not configured.");
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirect)
                + "&scope=" + enc("https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly")
                + "&access_type=offline"
                + "&state=" + enc(state);
    }

    private Map<String, Object> googleExchange(String code, String redirect) throws Exception {
        String clientId = credentialsService.getCredential("oauth_youtube", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_youtube", "client_secret");
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("YouTube/Google OAuth is not configured.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", code);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirect);
        form.put("grant_type", "authorization_code");
        JsonNode res = postForm("https://oauth2.googleapis.com/token", form, null);
        return tokenResult(res, "access_token", "refresh_token", "expires_in");
    }

    private Map<String, Object> googleRefresh(String refreshToken) throws Exception {
        String clientId = credentialsService.getCredential("oauth_youtube", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_youtube", "client_secret");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId != null ? clientId : "");
        form.put("client_secret", clientSecret != null ? clientSecret : "");
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        JsonNode res = postForm("https://oauth2.googleapis.com/token", form, null);
        Map<String, Object> result = tokenResult(res, "access_token", null, "expires_in");
        if (result.get("access_token") == null) {
            throw new IllegalStateException("Google token refresh failed: " + res);
        }
        result.put("refresh_token", refreshToken);
        return result;
    }

    // ── TikTok ───────────────────────────────────────────────────────────

    private String tiktokAuthUrl(String state, String redirect) {
        String clientKey = credentialsService.getCredential("oauth_tiktok", "client_id");
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalStateException("TikTok OAuth is not configured.");
        }
        return "https://www.tiktok.com/v2/auth/authorize?"
                + "client_key=" + enc(clientKey)
                + "&redirect_uri=" + enc(redirect)
                + "&response_type=code"
                + "&scope=" + enc("user.info.basic,video.publish")
                + "&state=" + enc(state);
    }

    private Map<String, Object> tiktokExchange(String code, String redirect) throws Exception {
        String clientKey = credentialsService.getCredential("oauth_tiktok", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_tiktok", "client_secret");
        if (clientKey == null || clientSecret == null) {
            throw new IllegalStateException("TikTok OAuth is not configured.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_key", clientKey);
        form.put("client_secret", clientSecret);
        form.put("grant_type", "authorization_code");
        form.put("auth_code", code);
        form.put("redirect_uri", redirect);
        JsonNode res = postForm("https://open.tiktokapis.com/v2/oauth/token/", form, null);
        JsonNode data = res.path("data");
        return tokenResult(data.isMissingNode() ? res : data, "access_token", "refresh_token", "expires_in");
    }

    private Map<String, Object> tiktokRefresh(String refreshToken) throws Exception {
        String clientKey = credentialsService.getCredential("oauth_tiktok", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_tiktok", "client_secret");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_key", clientKey != null ? clientKey : "");
        form.put("client_secret", clientSecret != null ? clientSecret : "");
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        JsonNode res = postForm("https://open.tiktokapis.com/v2/oauth/token/", form, null);
        JsonNode data = res.path("data");
        Map<String, Object> result = tokenResult(data.isMissingNode() ? res : data, "access_token", "refresh_token", "expires_in");
        if (result.get("access_token") == null) {
            throw new IllegalStateException("TikTok token refresh failed: " + res);
        }
        return result;
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode postForm(String url, Map<String, String> form, String basicAuthHeader) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(enc(e.getKey())).append('=').append(enc(e.getValue() != null ? e.getValue() : ""));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (basicAuthHeader != null) {
            builder.header("Authorization", basicAuthHeader);
        }
        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private String basicAuth(String clientId, String clientSecret) {
        String creds = (clientId != null ? clientId : "") + ":" + (clientSecret != null ? clientSecret : "");
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> tokenResult(JsonNode res, String tokenField, String refreshField, String expiresField) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", textOrNull(res, tokenField));
        result.put("refresh_token", refreshField != null ? textOrNull(res, refreshField) : null);
        JsonNode expires = res != null ? res.path(expiresField) : null;
        result.put("expires_in", expires != null && expires.isNumber() ? expires.asLong() : null);
        return result;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
