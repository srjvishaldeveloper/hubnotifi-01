package com.whatsmine.controller.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappPhoneNumber;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappPhoneNumberRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.IntegrationCredentialsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports php/app/Modules/Inbox/Http/Controllers/InboxSetupController.php — the
 * real, unified "Channel Setup" page (WhatsApp + Instagram + Messenger
 * together via Meta's real Embedded Signup flow), which nothing in Java
 * pointed to before (client.inbox.setup was mapped to a simpler,
 * WhatsApp-less manual-entry stand-in page).
 */
@RestController
@RequestMapping("/app/inbox/setup")
public class InboxSetupController {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final WhatsappBusinessAccountRepository wabaRepository;
    private final WhatsappPhoneNumberRepository phoneNumberRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final AiChatbotRepository chatbotRepository;
    private final IntegrationCredentialsService integrationCredentialsService;
    private final ObjectMapper objectMapper;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public InboxSetupController(WhatsappBusinessAccountRepository wabaRepository,
                                 WhatsappPhoneNumberRepository phoneNumberRepository,
                                 ChannelAccountRepository channelAccountRepository,
                                 AiChatbotRepository chatbotRepository,
                                 IntegrationCredentialsService integrationCredentialsService,
                                 ObjectMapper objectMapper) {
        this.wabaRepository = wabaRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.channelAccountRepository = channelAccountRepository;
        this.chatbotRepository = chatbotRepository;
        this.integrationCredentialsService = integrationCredentialsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = userDetails.getWorkspaceId();

        List<WhatsappBusinessAccount> wabas = wabaRepository.findByWorkspaceId(workspaceId);
        List<ChannelAccount> whatsappAccounts = channelAccountRepository.findByWorkspaceId(workspaceId).stream()
                .filter(a -> "whatsapp".equalsIgnoreCase(a.getChannel()) && a.getPhoneNumberId() != null)
                .toList();

        Map<Long, String> webhookTokensByWaba = new LinkedHashMap<>();
        Map<Long, List<String>> channelAccountPhoneIdsByWaba = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> channelAccountsByWaba = new LinkedHashMap<>();
        List<Map<String, Object>> wabaRows = new ArrayList<>();

        for (WhatsappBusinessAccount waba : wabas) {
            webhookTokensByWaba.put(waba.getId(), waba.getWebhookVerifyToken());

            List<ChannelAccount> accountsForWaba = whatsappAccounts.stream()
                    .filter(a -> waba.getWabaId().equals(a.getBusinessAccountId()))
                    .toList();
            channelAccountPhoneIdsByWaba.put(waba.getId(), accountsForWaba.stream().map(ChannelAccount::getPhoneNumberId).toList());
            channelAccountsByWaba.put(waba.getId(), accountsForWaba.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("phone_number_id", a.getPhoneNumberId());
                m.put("display_name", a.getDisplayName());
                m.put("status", a.getStatus());
                m.put("ai_chatbot_id", parseJson(a.getMetaJson()).get("ai_chatbot_id"));
                return m;
            }).toList());

            List<WhatsappPhoneNumber> phones = phoneNumberRepository.findByWabaIdFk(waba.getId());
            Map<String, Object> wabaRow = new LinkedHashMap<>();
            wabaRow.put("id", waba.getId());
            wabaRow.put("waba_id", waba.getWabaId());
            wabaRow.put("status", waba.getStatus());
            wabaRow.put("phone_numbers", phones.stream().map(this::phoneNumberRow).toList());
            wabaRows.add(wabaRow);
        }

        List<Map<String, Object>> instagramAccounts = channelAccountRows(workspaceId, "instagram");
        List<Map<String, Object>> messengerAccounts = channelAccountRows(workspaceId, "messenger");

        List<Map<String, Object>> chatbots = chatbotRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream()
                .filter(AiChatbot::isEnabled)
                .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName()))
                .toList();

        String metaAppId = integrationCredentialsService.getCredential("meta_app", "app_id");
        String metaVerifyToken = integrationCredentialsService.getCredential("meta_app", "verify_token");
        String metaConfigIdWhatsapp = integrationCredentialsService.getCredential("meta_app", "config_id_whatsapp");
        String metaConfigIdSocial = integrationCredentialsService.getCredential("meta_app", "config_id_social");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("wabas", wabaRows);
        props.put("whatsappWebhookUrl", appUrl + "/webhooks/whatsapp");
        props.put("whatsappWebhookGlobalUrl", appUrl + "/webhooks/whatsapp/global");
        props.put("webhookTokensByWaba", webhookTokensByWaba);
        props.put("channelAccountPhoneIdsByWaba", channelAccountPhoneIdsByWaba);
        props.put("channelAccountsByWaba", channelAccountsByWaba);
        props.put("instagramAccounts", instagramAccounts);
        props.put("messengerAccounts", messengerAccounts);
        props.put("chatbots", chatbots);
        props.put("metaWebhookUrl", metaVerifyToken != null ? appUrl + "/webhooks/meta/" + metaVerifyToken : null);
        props.put("metaAppId", metaAppId);
        props.put("metaConfigIdWhatsapp", metaConfigIdWhatsapp);
        props.put("metaConfigIdSocial", metaConfigIdSocial);

        return Inertia.render("Inbox/Setup", props);
    }

    @PostMapping("/embedded-signup/instagram")
    @SuppressWarnings("unchecked")
    public Object embeddedSignupInstagram(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = userDetails.getWorkspaceId();
        String code = str(body.get("code"));
        if (code == null || code.isBlank()) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "A code is required.");
        }

        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        if (appId == null || appId.isBlank()) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Meta App credentials are not configured. Please ask your administrator to configure them in Admin → Integrations → Meta App.");
        }

        registerAppWebhook("instagram", "messages,messaging_postbacks,message_reactions");

        String accessToken = exchangeCodeForToken(code);
        if (accessToken == null) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to exchange authorization code with Meta.");
        }
        String longToken = exchangeForLongLivedToken(accessToken);

        Map<String, Object> pagesResp;
        try {
            pagesResp = graphGet("me/accounts", Map.of("fields", "id,name,access_token,instagram_business_account{id,name,username}", "limit", "50"), longToken);
        } catch (Exception e) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Could not fetch your Facebook pages: " + e.getMessage());
        }
        if (!Boolean.TRUE.equals(pagesResp.get("_success"))) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Could not fetch your Facebook pages: " + graphErrorMessage(pagesResp));
        }

        List<Map<String, Object>> pages = pagesResp.get("data") instanceof List ? (List<Map<String, Object>>) pagesResp.get("data") : List.of();
        int connected = 0;

        for (Map<String, Object> page : pages) {
            Object igObj = page.get("instagram_business_account");
            if (!(igObj instanceof Map)) continue;
            Map<String, Object> ig = (Map<String, Object>) igObj;
            String igId = str(ig.get("id"));
            if (igId == null || igId.isBlank()) continue;

            String pageId = str(page.get("id"));
            String pageToken = str(page.get("access_token")) != null ? str(page.get("access_token")) : longToken;
            String name = firstNonBlank(str(ig.get("username")), str(ig.get("name")), str(page.get("name")), igId);

            subscribePageTo(pageId, pageToken, "messages,messaging_postbacks,message_reactions,message_reads");

            Map<String, Object> credentials = Map.of("access_token", pageToken, "instagram_account_id", igId);
            Map<String, Object> metaJson = new LinkedHashMap<>();
            metaJson.put("instagram_page_id", igId);
            metaJson.put("instagram_account_id", igId);
            metaJson.put("facebook_page_id", pageId);

            ChannelAccount existing = findByMetaJsonKey(workspaceId, "instagram", "instagram_page_id", igId);
            if (existing != null) {
                Map<String, Object> merged = new LinkedHashMap<>(parseJson(existing.getMetaJson()));
                merged.putAll(metaJson);
                existing.setCredentials(writeJson(credentials));
                existing.setMetaJson(writeJson(merged));
                existing.setStatus("active");
                channelAccountRepository.save(existing);
            } else {
                ChannelAccount ca = new ChannelAccount();
                ca.setWorkspaceId(workspaceId);
                ca.setChannel("instagram");
                ca.setProvider("meta");
                ca.setDisplayName(name.length() > 128 ? name.substring(0, 128) : name);
                ca.setCredentials(writeJson(credentials));
                ca.setMetaJson(writeJson(metaJson));
                ca.setStatus("active");
                channelAccountRepository.save(ca);
            }
            connected++;
        }

        if (connected == 0) {
            String message = pages.isEmpty()
                    ? "No Facebook Pages were returned. Make sure you granted page access during authorization and your Meta App has the pages_show_list permission in its Social config."
                    : "No Instagram Business accounts were found on your " + pages.size() + " authorized page(s). To fix this: (1) Go to Meta Business Suite → your Facebook Page → Linked Accounts → link your Instagram account. (2) Make sure your Instagram is a Professional (Business or Creator) account. (3) Ensure your Social Embedded Signup config includes the instagram_basic permission.";
            return err(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        return Map.of("success", true, "connected", connected);
    }

    @PostMapping("/embedded-signup/messenger")
    @SuppressWarnings("unchecked")
    public Object embeddedSignupMessenger(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = userDetails.getWorkspaceId();
        String code = str(body.get("code"));
        if (code == null || code.isBlank()) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "A code is required.");
        }

        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        if (appId == null || appId.isBlank()) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Meta App credentials are not configured. Please ask your administrator to configure them in Admin → Integrations → Meta App.");
        }

        registerAppWebhook("page", "messages,messaging_postbacks,messaging_optins,message_deliveries,message_reads");

        String accessToken = exchangeCodeForToken(code);
        if (accessToken == null) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to exchange authorization code with Meta.");
        }
        String longToken = exchangeForLongLivedToken(accessToken);

        Map<String, Object> pagesResp;
        try {
            pagesResp = graphGet("me/accounts", Map.of("fields", "id,name,access_token", "limit", "50"), longToken);
        } catch (Exception e) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Could not fetch your Facebook pages: " + e.getMessage());
        }
        if (!Boolean.TRUE.equals(pagesResp.get("_success"))) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "Could not fetch your Facebook pages: " + graphErrorMessage(pagesResp));
        }

        List<Map<String, Object>> pages = pagesResp.get("data") instanceof List ? (List<Map<String, Object>>) pagesResp.get("data") : List.of();
        int connected = 0;

        for (Map<String, Object> page : pages) {
            String pageId = str(page.get("id"));
            if (pageId == null || pageId.isBlank()) continue;
            String pageName = firstNonBlank(str(page.get("name")), pageId);
            String pageToken = str(page.get("access_token"));

            if (pageToken == null) {
                try {
                    Map<String, Object> tokenResp = graphGet(pageId, Map.of("fields", "access_token"), longToken);
                    pageToken = str(tokenResp.get("access_token"));
                } catch (Exception ignored) { }
            }
            if (pageToken == null) continue;

            subscribePageTo(pageId, pageToken, "messages,messaging_postbacks,messaging_optins,message_deliveries,message_reads");

            ChannelAccount existing = findByMetaJsonKey(workspaceId, "messenger", "page_id", pageId);
            if (existing != null) {
                Map<String, Object> merged = new LinkedHashMap<>(parseJson(existing.getMetaJson()));
                merged.put("page_id", pageId);
                existing.setCredentials(writeJson(Map.of("page_access_token", pageToken)));
                existing.setMetaJson(writeJson(merged));
                existing.setStatus("active");
                channelAccountRepository.save(existing);
            } else {
                ChannelAccount ca = new ChannelAccount();
                ca.setWorkspaceId(workspaceId);
                ca.setChannel("messenger");
                ca.setProvider("meta");
                ca.setDisplayName(pageName.length() > 128 ? pageName.substring(0, 128) : pageName);
                ca.setCredentials(writeJson(Map.of("page_access_token", pageToken)));
                ca.setMetaJson(writeJson(Map.of("page_id", pageId)));
                ca.setStatus("active");
                channelAccountRepository.save(ca);
            }
            connected++;
        }

        if (connected == 0) {
            return err(HttpStatus.UNPROCESSABLE_ENTITY, "No Facebook Pages found on your account. Make sure you manage at least one Facebook Page.");
        }
        return Map.of("success", true, "connected", connected);
    }

    @PatchMapping("/{channelAccountId}/chatbot")
    public Object assignChatbot(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long channelAccountId, @RequestBody Map<String, Object> body) {
        ChannelAccount ca = channelAccountRepository.findById(channelAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ca.getWorkspaceId().equals(userDetails.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Object chatbotIdObj = body.get("chatbot_id");
        Long chatbotId = chatbotIdObj != null ? Long.valueOf(chatbotIdObj.toString()) : null;

        if (chatbotId != null) {
            boolean exists = chatbotRepository.findByWorkspaceIdAndId(userDetails.getWorkspaceId(), chatbotId)
                    .map(AiChatbot::isEnabled).orElse(false);
            if (!exists) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Chatbot not found or not enabled.");
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>(parseJson(ca.getMetaJson()));
        if (chatbotId == null) {
            meta.remove("ai_chatbot_id");
        } else {
            meta.put("ai_chatbot_id", chatbotId);
        }
        ca.setMetaJson(writeJson(meta));
        channelAccountRepository.save(ca);

        return Inertia.redirect("/app/inbox/setup");
    }

    @DeleteMapping("/{channelAccountId}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long channelAccountId) {
        ChannelAccount ca = channelAccountRepository.findById(channelAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ca.getWorkspaceId().equals(userDetails.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"instagram".equalsIgnoreCase(ca.getChannel()) && !"messenger".equalsIgnoreCase(ca.getChannel())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        channelAccountRepository.delete(ca);
        return Inertia.redirect("/app/inbox/setup");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> channelAccountRows(Long workspaceId, String channel) {
        return channelAccountRepository.findByWorkspaceId(workspaceId).stream()
                .filter(a -> channel.equalsIgnoreCase(a.getChannel()))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("display_name", a.getDisplayName());
                    m.put("status", a.getStatus());
                    m.put("created_at", a.getCreatedAt());
                    m.put("ai_chatbot_id", parseJson(a.getMetaJson()).get("ai_chatbot_id"));
                    return m;
                }).toList();
    }

    private Map<String, Object> phoneNumberRow(WhatsappPhoneNumber p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phone_number_id", p.getPhoneNumberId());
        m.put("display_phone", p.getDisplayPhone());
        m.put("verified_name", p.getVerifiedName());
        m.put("quality_rating", p.getQualityRating());
        m.put("code_verification_status", p.getCodeVerificationStatus());
        m.put("name_status", p.getNameStatus());
        return m;
    }

    private ChannelAccount findByMetaJsonKey(Long workspaceId, String channel, String key, String value) {
        return channelAccountRepository.findByWorkspaceId(workspaceId).stream()
                .filter(a -> channel.equalsIgnoreCase(a.getChannel()))
                .filter(a -> value.equals(str(parseJson(a.getMetaJson()).get(key))))
                .findFirst().orElse(null);
    }

    private String exchangeCodeForToken(String code) {
        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        String appSecret = integrationCredentialsService.getCredential("meta_app", "app_secret");
        if (appId == null || appSecret == null) return null;
        try {
            Map<String, Object> resp = graphGetAbsolute("https://graph.facebook.com/v20.0/oauth/access_token",
                    Map.of("client_id", appId, "client_secret", appSecret, "code", code, "redirect_uri", ""));
            if (Boolean.TRUE.equals(resp.get("_success")) && resp.get("access_token") != null) {
                return str(resp.get("access_token"));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String exchangeForLongLivedToken(String shortToken) {
        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        String appSecret = integrationCredentialsService.getCredential("meta_app", "app_secret");
        if (appId == null || appSecret == null) return shortToken;
        try {
            Map<String, Object> resp = graphGetAbsolute("https://graph.facebook.com/v20.0/oauth/access_token",
                    Map.of("grant_type", "fb_exchange_token", "client_id", appId, "client_secret", appSecret, "fb_exchange_token", shortToken));
            if (Boolean.TRUE.equals(resp.get("_success")) && resp.get("access_token") != null) {
                return str(resp.get("access_token"));
            }
            return shortToken;
        } catch (Exception e) {
            return shortToken;
        }
    }

    /** Registers the app-level webhook subscription (object=page|instagram) so Meta knows our callback URL. Best-effort, matching PHP. */
    private void registerAppWebhook(String object, String fields) {
        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        String appSecret = integrationCredentialsService.getCredential("meta_app", "app_secret");
        String verifyToken = integrationCredentialsService.getCredential("meta_app", "verify_token");
        if (appId == null || appSecret == null || verifyToken == null) return;

        try {
            String callbackUrl = appUrl + "/webhooks/meta/" + verifyToken;
            postForm("https://graph.facebook.com/v20.0/" + appId + "/subscriptions", Map.of(
                    "access_token", appId + "|" + appSecret,
                    "object", object,
                    "callback_url", callbackUrl,
                    "verify_token", verifyToken,
                    "fields", fields));
        } catch (Exception ignored) {
            // Best-effort — a failure here is logged server-side by postForm's caller context, matching PHP's try/catch.
        }
    }

    private void subscribePageTo(String pageId, String pageToken, String fields) {
        if (pageId == null || pageId.isBlank()) return;
        try {
            postFormBearer("https://graph.facebook.com/v20.0/" + pageId + "/subscribed_apps", Map.of("subscribed_fields", fields), pageToken);
        } catch (Exception ignored) { }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphGet(String path, Map<String, String> query, String accessToken) throws Exception {
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v20.0/" + path + "?" + qs))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphGetAbsolute(String url, Map<String, String> query) throws Exception {
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + qs)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, Map<String, String> form) throws Exception {
        String body = formEncode(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postFormBearer(String url, Map<String, String> form, String token) throws Exception {
        String body = formEncode(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
        return parsed;
    }

    private String formEncode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    private String enc(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private String graphErrorMessage(Map<String, Object> resp) {
        Object error = resp.get("error");
        if (error instanceof Map) {
            Object msg = ((Map<String, Object>) error).get("message");
            if (msg != null) return String.valueOf(msg);
        }
        return "unknown error";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Clean {"message": ...} error body matching PHP's response()->json(['message' => ...], status) shape — Inbox/Setup.jsx reads json.message directly. */
    private ResponseEntity<Map<String, Object>> err(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
