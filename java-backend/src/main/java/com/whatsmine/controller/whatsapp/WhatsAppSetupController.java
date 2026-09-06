package com.whatsmine.controller.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappPhoneNumber;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappPhoneNumberRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.IntegrationCredentialsService;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/app/whatsapp/setup")
public class WhatsAppSetupController {

    private final WhatsappBusinessAccountRepository wabaRepository;
    private final WhatsappPhoneNumberRepository phoneNumberRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final WhatsAppApiClient whatsAppApiClient;
    private final IntegrationCredentialsService integrationCredentialsService;
    private final ObjectMapper objectMapper;
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();

    @org.springframework.beans.factory.annotation.Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public WhatsAppSetupController(
            WhatsappBusinessAccountRepository wabaRepository,
            WhatsappPhoneNumberRepository phoneNumberRepository,
            ChannelAccountRepository channelAccountRepository,
            WhatsAppApiClient whatsAppApiClient,
            IntegrationCredentialsService integrationCredentialsService,
            ObjectMapper objectMapper) {
        this.wabaRepository = wabaRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.channelAccountRepository = channelAccountRepository;
        this.whatsAppApiClient = whatsAppApiClient;
        this.integrationCredentialsService = integrationCredentialsService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/embedded-signup")
    @Transactional
    public Object embeddedSignup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody EmbeddedSignupRequest request,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();

        Optional<WhatsappBusinessAccount> existingOpt = wabaRepository.findByWorkspaceIdAndWabaId(workspaceId, request.getWabaId());

        WhatsappBusinessAccount waba = existingOpt.orElseGet(WhatsappBusinessAccount::new);
        waba.setWorkspaceId(workspaceId);
        waba.setWabaId(request.getWabaId());
        waba.setWebhookVerifyToken(request.getWebhookVerifyToken() != null ? request.getWebhookVerifyToken() : "waba_token_" + System.currentTimeMillis());
        waba.setStatus("active");
        waba = wabaRepository.save(waba);

        if (request.getPhoneNumberId() != null) {
            WhatsappPhoneNumber phone = new WhatsappPhoneNumber();
            phone.setWabaIdFk(waba.getId());
            phone.setPhoneNumberId(request.getPhoneNumberId());
            phone.setDisplayPhone(request.getDisplayPhone() != null ? request.getDisplayPhone() : "+1234567890");
            phone.setQualityRating("GREEN");
            phoneNumberRepository.save(phone);
        }

        Inertia.flashSuccess(session, "WhatsApp Business Account connected successfully.");
        return Inertia.redirect("/app/dashboard");
    }

    @DeleteMapping("/{wabaId}")
    @Transactional
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String wabaId,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();
        Optional<WhatsappBusinessAccount> wabaOpt = wabaRepository.findByWabaId(wabaId);

        if (wabaOpt.isPresent()) {
            WhatsappBusinessAccount waba = wabaOpt.get();
            if (!waba.getWorkspaceId().equals(workspaceId)) {
                throw new AccessDeniedException("Access denied to requested WhatsApp account.");
            }
            wabaRepository.delete(waba);
        }

        Inertia.flashSuccess(session, "WhatsApp Business Account disconnected.");
        return Inertia.redirect("/app/dashboard");
    }

    /** Imports/refreshes every phone number Meta has assigned to this WABA, ported from WhatsappSetupController::syncPhoneNumbers(). */
    @PostMapping("/{wabaId}/sync-phone-numbers")
    public Object syncPhoneNumbers(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String wabaId, HttpSession session) {
        WhatsappBusinessAccount waba = authorizeWaba(userDetails, wabaId);

        String token = metaAccessToken(waba);
        if (token.isBlank()) {
            Inertia.flashError(session, "No system user access token available for this workspace.");
            return Inertia.redirect("/app/dashboard");
        }

        int count;
        try {
            List<Map<String, Object>> rows = whatsAppApiClient.fetchWabaPhoneNumbers(waba.getWabaId(), token);
            count = 0;
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                if (idObj == null) continue;
                String phoneNumberId = String.valueOf(idObj);
                Map<String, Object> details = whatsAppApiClient.fetchPhoneNumberDetails(phoneNumberId, token);
                Map<String, Object> merged = new java.util.LinkedHashMap<>(row);
                if (details != null) merged.putAll(details);
                attachPhoneNumberToWaba(waba, phoneNumberId, merged);
                count++;
            }
        } catch (Exception e) {
            Inertia.flashError(session, "Could not load phone numbers from Meta. Check the WABA ID, that numbers are assigned in Business Manager, and that your system user token includes whatsapp_business_management.");
            return Inertia.redirect("/app/dashboard");
        }

        if (count == 0) {
            Inertia.flashError(session, "Meta returned no phone numbers for this WABA. Confirm the number is added to this exact account in Meta Business Suite, then try again.");
        } else {
            Inertia.flashSuccess(session, "Synced " + count + " phone number(s) from Meta.");
        }
        return Inertia.redirect("/app/dashboard");
    }

    /** Refetches a phone number's verification/quality status from Meta, ported from WhatsappSetupController::refreshPhoneStatus(). */
    @PostMapping("/{wabaId}/phone/{phoneNumberId}/refresh-status")
    public ResponseEntity<Map<String, Object>> refreshPhoneStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String wabaId,
            @PathVariable String phoneNumberId) {
        WhatsappBusinessAccount waba = authorizeWaba(userDetails, wabaId);

        String token = metaAccessToken(waba);
        if (token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No access token available."));
        }

        Map<String, Object> details = whatsAppApiClient.fetchPhoneNumberDetails(phoneNumberId, token);
        if (details == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Could not fetch phone number details from Meta."));
        }

        WhatsappPhoneNumber phone = phoneNumberRepository.findByPhoneNumberId(phoneNumberId).orElse(null);
        if (phone != null) {
            applyPhoneDetails(phone, details);
            phoneNumberRepository.save(phone);
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>(details);
        data.put("phone_number_id", phoneNumberId);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** Submits a verified-name change request to Meta (asynchronously reviewed), ported from WhatsappSetupController::changeDisplayName(). */
    @PostMapping("/{wabaId}/phone/{phoneNumberId}/change-name")
    public ResponseEntity<Map<String, Object>> changeDisplayName(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String wabaId,
            @PathVariable String phoneNumberId,
            @RequestBody Map<String, Object> body) {
        WhatsappBusinessAccount waba = authorizeWaba(userDetails, wabaId);

        Object nameObj = body.get("name");
        String name = nameObj != null ? nameObj.toString().trim() : "";
        if (name.isEmpty() || name.length() > 100) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "A name between 1 and 100 characters is required."));
        }

        String token = metaAccessToken(waba);
        if (token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No access token available."));
        }

        Map<String, Object> result = whatsAppApiClient.requestDisplayNameChange(phoneNumberId, name, token);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = result.get("response") instanceof Map ? (Map<String, Object>) result.get("response") : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> metaError = response.get("error") instanceof Map ? (Map<String, Object>) response.get("error") : Map.of();
            String errMsg = firstNonBlank(
                    str(metaError.get("error_user_msg")), str(metaError.get("message")),
                    "Meta rejected the name change. Code: " + (metaError.get("code") != null ? metaError.get("code") : "unknown"));
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", errMsg));
        }

        WhatsappPhoneNumber phone = phoneNumberRepository.findByPhoneNumberId(phoneNumberId).orElse(null);
        if (phone != null) {
            phone.setNameStatus("PENDING_REVIEW");
            phone.setRequestedVerifiedName(name);
            phoneNumberRepository.save(phone);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Display name change submitted. Meta will review it — this usually takes a few minutes to 24 hours."));
    }

    /**
     * Re-subscribes our Meta App to a WABA's events and re-registers the
     * global webhook callback URL — ported from
     * WhatsappEmbeddedSignupController::reregisterWebhook()/subscribeWabaWebhooks().
     * Uses Java's own configured global verify token (WhatsAppApiClient's
     * whatsapp.api.global-verify-token) rather than PHP's SHA-256 formula, so
     * the token Meta is told to use matches what this app's own
     * /webhooks/whatsapp/global GET-verify handler actually checks against.
     */
    @PostMapping("/{wabaId}/reregister-webhook")
    public ResponseEntity<Map<String, Object>> reregisterWebhook(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String wabaId) {
        WhatsappBusinessAccount waba = authorizeWaba(userDetails, wabaId);

        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        String appSecret = integrationCredentialsService.getCredential("meta_app", "app_secret");
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", "Meta App credentials not configured."));
        }

        String userToken = metaAccessToken(waba);
        if (userToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", "No access token available for this WABA."));
        }

        String webhookError = subscribeWabaWebhooks(waba.getWabaId(), userToken, appId, appSecret);
        if (webhookError != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("success", false, "message", webhookError));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Webhook re-registered with Meta."));
    }

    private String subscribeWabaWebhooks(String wabaId, String userToken, String appId, String appSecret) {
        String appToken = appId + "|" + appSecret;

        // Step 1: subscribe our Meta App to this WABA's events (app token first, user token as fallback).
        // Best-effort, matching PHP — a failure here is logged but doesn't abort re-registration.
        try {
            Map<String, Object> subResult = postForm("https://graph.facebook.com/v20.0/" + wabaId + "/subscribed_apps", Map.of("access_token", appToken));
            if (!Boolean.TRUE.equals(subResult.get("success"))) {
                postForm("https://graph.facebook.com/v20.0/" + wabaId + "/subscribed_apps", Map.of("access_token", userToken));
            }
        } catch (Exception ignored) { }

        // Step 2: register the global callback URL for the whole Meta App.
        try {
            String callbackUrl = appUrl + "/webhooks/whatsapp/global";
            Map<String, String> form = new java.util.LinkedHashMap<>();
            form.put("access_token", appToken);
            form.put("object", "whatsapp_business_account");
            form.put("callback_url", callbackUrl);
            form.put("verify_token", whatsAppApiClient.getGlobalVerifyToken());
            form.put("fields", "messages,message_template_status_update,phone_number_name_update,phone_number_quality_update,account_update");

            Map<String, Object> response = postForm("https://graph.facebook.com/v20.0/" + appId + "/subscriptions", form);
            if (Boolean.TRUE.equals(response.get("success"))) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> error = response.get("error") instanceof Map ? (Map<String, Object>) response.get("error") : Map.of();
            return "Meta rejected the webhook subscription: " + (error.get("message") != null ? error.get("message") : "unknown error");
        } catch (Exception e) {
            return "Could not reach Meta to register the webhook: " + e.getMessage();
        }
    }

    private Map<String, Object> postForm(String url, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                    .append('=').append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = response.body() != null && !response.body().isBlank()
                ? objectMapper.readValue(response.body(), Map.class) : new java.util.LinkedHashMap<>();
        parsed.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
        return parsed;
    }

    private WhatsappBusinessAccount authorizeWaba(CustomUserDetails userDetails, String wabaId) {
        WhatsappBusinessAccount waba = wabaRepository.findByWabaId(wabaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!waba.getWorkspaceId().equals(userDetails.getWorkspaceId())) {
            throw new AccessDeniedException("Access denied to requested WhatsApp account.");
        }
        return waba;
    }

    /** Per-WABA override (credentials.system_user_token) first, else the system-level Meta App System User Token. */
    @SuppressWarnings("unchecked")
    private String metaAccessToken(WhatsappBusinessAccount waba) {
        String raw = waba.getCredentials();
        if (raw != null && !raw.isBlank()) {
            try {
                Map<String, Object> creds = objectMapper.readValue(raw, Map.class);
                Object token = creds.get("system_user_token");
                if (token != null && !token.toString().isBlank()) {
                    return token.toString();
                }
            } catch (Exception ignored) { }
        }
        String systemToken = integrationCredentialsService.getCredential("meta_app", "system_user_token");
        return systemToken != null ? systemToken : "";
    }

    private void applyPhoneDetails(WhatsappPhoneNumber phone, Map<String, Object> details) {
        if (details.get("display_phone_number") != null) phone.setDisplayPhone(str(details.get("display_phone_number")));
        if (details.get("verified_name") != null) phone.setVerifiedName(str(details.get("verified_name")));
        if (details.get("quality_rating") != null) phone.setQualityRating(str(details.get("quality_rating")));
        String tier = messagingLimitTier(details);
        if (tier != null) phone.setMessagingLimitTier(tier);
        if (details.get("code_verification_status") != null) phone.setCodeVerificationStatus(str(details.get("code_verification_status")));
        if (details.get("name_status") != null) phone.setNameStatus(str(details.get("name_status")));
        if (details.get("requested_verified_name") != null) phone.setRequestedVerifiedName(str(details.get("requested_verified_name")));
        if (details.get("account_mode") != null) phone.setAccountMode(str(details.get("account_mode")));
    }

    @SuppressWarnings("unchecked")
    private String messagingLimitTier(Map<String, Object> row) {
        Object tier = row.get("messaging_limit_tier");
        if (tier instanceof String) return (String) tier;
        Object throughput = row.get("throughput");
        if (throughput instanceof Map) {
            Object level = ((Map<String, Object>) throughput).get("level");
            if (level instanceof String) return (String) level;
        }
        return null;
    }

    /** Upserts the local WhatsappPhoneNumber + ChannelAccount from a Meta phone-numbers row, ported from WhatsappSetupController::attachPhoneNumberToWaba(). */
    private void attachPhoneNumberToWaba(WhatsappBusinessAccount waba, String phoneNumberId, Map<String, Object> metaRow) {
        WhatsappPhoneNumber phone = phoneNumberRepository.findByPhoneNumberId(phoneNumberId).orElseGet(WhatsappPhoneNumber::new);
        phone.setWabaIdFk(waba.getId());
        phone.setPhoneNumberId(phoneNumberId);
        applyPhoneDetails(phone, metaRow);
        phoneNumberRepository.save(phone);

        ChannelAccount account = channelAccountRepository.findByPhoneNumberIdAndChannel(phoneNumberId, "whatsapp").orElseGet(ChannelAccount::new);
        boolean isNew = account.getId() == null;
        account.setWorkspaceId(waba.getWorkspaceId());
        account.setPhoneNumberId(phoneNumberId);
        account.setChannel("whatsapp");
        account.setProvider("meta");
        account.setBusinessAccountId(waba.getWabaId());
        account.setStatus("active");

        String label = firstNonBlank(str(metaRow.get("verified_name")), str(metaRow.get("display_phone_number")));
        if (label != null && !label.isBlank()) {
            account.setDisplayName(label.length() > 128 ? label.substring(0, 128) : label);
        } else if (isNew) {
            account.setDisplayName("WhatsApp");
        }
        channelAccountRepository.save(account);
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    public static class EmbeddedSignupRequest {
        @NotBlank
        private String wabaId;
        private String phoneNumberId;
        private String displayPhone;
        private String webhookVerifyToken;

        public String getWabaId() { return wabaId; }
        public void setWabaId(String wabaId) { this.wabaId = wabaId; }
        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getDisplayPhone() { return displayPhone; }
        public void setDisplayPhone(String displayPhone) { this.displayPhone = displayPhone; }
        public String getWebhookVerifyToken() { return webhookVerifyToken; }
        public void setWebhookVerifyToken(String webhookVerifyToken) { this.webhookVerifyToken = webhookVerifyToken; }
    }
}
