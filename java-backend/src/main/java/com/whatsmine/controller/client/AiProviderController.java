package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.AiProviderConfig;
import com.whatsmine.repository.AiProviderConfigRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ai/providers")
public class AiProviderController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AiProviderConfigRepository providerConfigRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<AiProviderConfig> configs = providerConfigRepository.findByWorkspaceId(workspaceId);

        Map<String, AiProviderConfig> configMap = new HashMap<>();
        for (AiProviderConfig c : configs) {
            configMap.put(c.getProvider(), c);
        }

        List<String> knownProviders = List.of("openai", "anthropic", "gemini");
        List<Map<String, Object>> providerList = new ArrayList<>();

        for (String p : knownProviders) {
            AiProviderConfig cfg = configMap.get(p);
            boolean enabled = cfg != null && cfg.isEnabled();
            boolean configured = cfg != null && cfg.getCredentials() != null && cfg.getCredentials().get("api_key") != null && !String.valueOf(cfg.getCredentials().get("api_key")).isBlank();
            String chatModel = cfg != null && cfg.getDefaultModelChat() != null ? cfg.getDefaultModelChat() : "";

            providerList.add(Map.of(
                    "provider", p,
                    "enabled", enabled,
                    "configured", configured,
                    "default_model_chat", chatModel
            ));
        }

        Map<String, Object> props = Map.of("providers", providerList);
        return inertiaRenderer.render("AI/Providers/Index", props, request);
    }

    @PutMapping("/{provider}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String provider,
            @RequestBody Map<String, Object> body
    ) {
        if (!List.of("openai", "anthropic", "gemini").contains(provider.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown AI provider.");
        }

        Long workspaceId = getWorkspaceId(userDetails);
        AiProviderConfig config = providerConfigRepository
                .findByWorkspaceIdAndProvider(workspaceId, provider.toLowerCase())
                .orElseGet(() -> {
                    AiProviderConfig newCfg = new AiProviderConfig();
                    newCfg.setWorkspaceId(workspaceId);
                    newCfg.setProvider(provider.toLowerCase());
                    return newCfg;
                });

        Map<String, Object> creds = config.getCredentials() != null ? new HashMap<>(config.getCredentials()) : new HashMap<>();
        String apiKey = body != null ? (String) body.get("api_key") : null;
        if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("•")) {
            creds.put("api_key", apiKey.trim());
        }
        config.setCredentials(creds);

        if (body != null) {
            if (body.containsKey("default_model_chat")) {
                config.setDefaultModelChat((String) body.get("default_model_chat"));
            }
            if (body.containsKey("default_model_embed")) {
                config.setDefaultModelEmbed((String) body.get("default_model_embed"));
            }
            if (body.containsKey("enabled")) {
                config.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
            }
        }

        providerConfigRepository.save(config);

        // Inertia.redirect() (not a raw ResponseEntity<Map>) — Inertia's
        // actual POST requests send "Accept: text/html, application/xhtml+xml",
        // which Jackson can't satisfy for a JSON body, failing content
        // negotiation with 406 before the redirect reaches the browser.
        return Inertia.redirect("/app/ai/providers");
    }
}
