package com.whatsmine.controller.broadcasting;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.SmsProviderConfig;
import com.whatsmine.repository.SmsProviderConfigRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-workspace SMS gateway configuration, porting PHP's SmsProviderController
 * / sms_provider_configs. Lists the same 14-provider catalog PHP does (so the
 * existing Broadcasting/SmsProviders/Index.jsx page renders identically), but
 * only "twilio" has a real sending driver (SmsApiClient) — the other 13 can
 * have credentials saved (for parity with PHP's page and future drivers) but
 * SmsApiClient honestly reports "not implemented" if one is ever set active,
 * rather than fabricating a send.
 */
@RestController
@RequestMapping("/app/broadcasts/sms-gateways")
public class SmsProviderController {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> FIELDS = new LinkedHashMap<>();

    static {
        LABELS.put("twilio", "Twilio");
        LABELS.put("nexmo", "Vonage (Nexmo)");
        LABELS.put("messagebird", "MessageBird");
        LABELS.put("plivo", "Plivo");
        LABELS.put("telnyx", "Telnyx");
        LABELS.put("infobip", "Infobip");
        LABELS.put("clicksend", "ClickSend");
        LABELS.put("smsbd", "SMSBD");
        LABELS.put("reve", "Reve SMS");
        LABELS.put("bulksmsbd", "BulkSMSBD");
        LABELS.put("sms_dot_bd", "SMS.com.bd");
        LABELS.put("mimsms", "MiMSMS");
        LABELS.put("fast2sms", "Fast2SMS");
        LABELS.put("amazon_sns", "Amazon SNS");

        FIELDS.put("twilio", List.of(
                field("account_sid", "Account SID", "text", true),
                field("auth_token", "Auth Token", "password", true),
                field("from_number", "From Number", "text", true)
        ));
        FIELDS.put("nexmo", List.of(field("api_key", "API Key", "text", true), field("api_secret", "API Secret", "password", true)));
        FIELDS.put("messagebird", List.of(field("api_key", "API Key", "password", true)));
        FIELDS.put("plivo", List.of(field("auth_id", "Auth ID", "text", true), field("auth_token", "Auth Token", "password", true)));
        FIELDS.put("telnyx", List.of(field("api_key", "API Key", "password", true), field("messaging_profile_id", "Messaging Profile ID", "text", false)));
        FIELDS.put("infobip", List.of(field("api_key", "API Key", "password", true), field("base_url", "Base URL", "text", true)));
        FIELDS.put("clicksend", List.of(field("username", "Username", "text", true), field("api_key", "API Key", "password", true)));
        FIELDS.put("smsbd", List.of(field("api_token", "API Token", "password", true)));
        FIELDS.put("reve", List.of(field("api_key", "API Key", "password", true)));
        FIELDS.put("bulksmsbd", List.of(field("api_key", "API Key", "password", true)));
        FIELDS.put("sms_dot_bd", List.of(field("api_key", "API Key", "password", true)));
        FIELDS.put("mimsms", List.of(field("api_key", "API Key", "password", true)));
        FIELDS.put("fast2sms", List.of(field("api_key", "API Key", "password", true), field("route", "Route (q/dlt)", "text", false)));
        FIELDS.put("amazon_sns", List.of(
                field("access_key", "Access Key", "text", true),
                field("secret_key", "Secret Key", "password", true),
                field("region", "Region", "text", true)
        ));
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("required", required);
        return f;
    }

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private SmsProviderConfigRepository smsProviderConfigRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        Map<String, SmsProviderConfig> byProvider = new LinkedHashMap<>();
        for (SmsProviderConfig config : smsProviderConfigRepository.findByWorkspaceId(workspaceId)) {
            byProvider.put(config.getProvider(), config);
        }

        List<Map<String, Object>> providers = new ArrayList<>();
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String provider = entry.getKey();
            SmsProviderConfig config = byProvider.get(provider);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider", provider);
            item.put("label", entry.getValue());
            item.put("fields", FIELDS.getOrDefault(provider, List.of()));
            item.put("configured", config != null && config.getCredentials() != null && !config.getCredentials().isEmpty());
            item.put("default", config != null && Boolean.TRUE.equals(config.getIsDefault()));
            item.put("sender_id", config != null ? config.getSenderId() : null);
            item.put("masked", maskedCredentials(config));
            providers.add(item);
        }

        return inertiaRenderer.render("Broadcasting/SmsProviders/Index", Map.of("providers", providers), request);
    }

    private Map<String, String> maskedCredentials(SmsProviderConfig config) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (config == null || config.getCredentials() == null) return masked;
        config.getCredentials().forEach((k, v) -> {
            String value = v != null ? String.valueOf(v) : "";
            masked.put(k, value.length() > 4 ? "••••" + value.substring(value.length() - 4) : "••••");
        });
        return masked;
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{provider}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String provider,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        if (!LABELS.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown SMS provider.");
        }

        SmsProviderConfig config = smsProviderConfigRepository.findByWorkspaceIdAndProvider(workspaceId, provider)
                .orElseGet(() -> {
                    SmsProviderConfig c = new SmsProviderConfig();
                    c.setWorkspaceId(workspaceId);
                    c.setProvider(provider);
                    return c;
                });

        // Merge-save: a blank/placeholder value for a field already on file
        // leaves the existing secret untouched (matches the masked-value UI).
        Map<String, Object> existing = config.getCredentials() != null ? new LinkedHashMap<>(config.getCredentials()) : new LinkedHashMap<>();
        Object credentialsObj = body.get("credentials");
        if (credentialsObj instanceof Map) {
            ((Map<String, Object>) credentialsObj).forEach((k, v) -> {
                String value = v != null ? String.valueOf(v) : "";
                if (!value.isBlank() && !value.startsWith("••••")) {
                    existing.put(k, value);
                }
            });
        }
        config.setCredentials(existing);

        if (body.containsKey("sender_id")) {
            config.setSenderId(body.get("sender_id") != null ? String.valueOf(body.get("sender_id")) : null);
        }

        boolean makeDefault = Boolean.TRUE.equals(body.get("default"));
        if (makeDefault) {
            // Only one default per workspace — clear any other provider's flag.
            for (SmsProviderConfig other : smsProviderConfigRepository.findByWorkspaceId(workspaceId)) {
                if (!other.getProvider().equals(provider) && Boolean.TRUE.equals(other.getIsDefault())) {
                    other.setIsDefault(false);
                    smsProviderConfigRepository.save(other);
                }
            }
        }
        config.setIsDefault(makeDefault);

        smsProviderConfigRepository.save(config);
        Inertia.flashSuccess(session, "SMS gateway saved.");
        return Inertia.redirect("/app/broadcasts/sms-gateways");
    }

    @DeleteMapping("/{provider}")
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String provider,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        smsProviderConfigRepository.findByWorkspaceIdAndProvider(workspaceId, provider)
                .ifPresent(smsProviderConfigRepository::delete);

        Inertia.flashSuccess(session, "SMS gateway removed.");
        return Inertia.redirect("/app/broadcasts/sms-gateways");
    }
}
