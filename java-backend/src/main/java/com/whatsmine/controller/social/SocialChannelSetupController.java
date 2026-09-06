package com.whatsmine.controller.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects a Facebook Page (Messenger) or Instagram Business Account
 * (Instagram DMs) the same way WhatsAppSetupController connects a WABA:
 * manual entry of the id + long-lived token, not a Facebook embedded-signup
 * OAuth wizard (that needs a live, App-Review-approved Meta App and a
 * frontend JS SDK integration — out of scope here, same reasoning as
 * WhatsApp's setup). Once connected, MetaMessagingWebhookController and
 * MetaMessagingApiClient use the id/token stored here for real.
 */
@RestController
@RequestMapping("/app/channels/social")
public class SocialChannelSetupController {

    private final ChannelAccountRepository channelAccountRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    private InertiaRenderer inertiaRenderer;

    public SocialChannelSetupController(ChannelAccountRepository channelAccountRepository, ObjectMapper objectMapper) {
        this.channelAccountRepository = channelAccountRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ChannelAccount> accounts = channelAccountRepository.findByWorkspaceId(userDetails.getWorkspaceId());

        List<Map<String, Object>> messengerAccounts = new ArrayList<>();
        List<Map<String, Object>> instagramAccounts = new ArrayList<>();
        for (ChannelAccount ca : accounts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ca.getId());
            row.put("display_name", ca.getDisplayName());
            row.put("status", ca.getStatus());
            if ("messenger".equalsIgnoreCase(ca.getChannel())) {
                messengerAccounts.add(row);
            } else if ("instagram".equalsIgnoreCase(ca.getChannel())) {
                instagramAccounts.add(row);
            }
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("messengerAccounts", messengerAccounts);
        props.put("instagramAccounts", instagramAccounts);
        return inertiaRenderer.render("Social/ChannelSetup/Index", props, request);
    }

    @PostMapping("/messenger")
    @Transactional
    public Object connectMessenger(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body, HttpSession session) {
        String pageId = str(body.get("page_id"));
        String pageName = str(body.get("page_name"));
        String pageAccessToken = str(body.get("page_access_token"));
        if (pageId == null || pageId.isBlank() || pageAccessToken == null || pageAccessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "page_id and page_access_token are required.");
        }

        Long workspaceId = userDetails.getWorkspaceId();
        ChannelAccount ca = findExisting(workspaceId, "messenger", "page_id", pageId);
        if (ca == null) {
            ca = new ChannelAccount();
            ca.setWorkspaceId(workspaceId);
            ca.setChannel("messenger");
        }
        ca.setProvider("meta");
        ca.setStatus("active");
        ca.setDisplayName(pageName != null ? pageName : "Facebook Page " + pageId);
        ca.setCredentials(writeJson(Map.of("page_access_token", pageAccessToken)));
        ca.setMetaJson(writeJson(Map.of("page_id", pageId)));
        channelAccountRepository.save(ca);

        Inertia.flashSuccess(session, "Messenger page connected.");
        return Inertia.redirect("/app/inbox");
    }

    @PostMapping("/instagram")
    @Transactional
    public Object connectInstagram(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body, HttpSession session) {
        String igAccountId = str(body.get("instagram_account_id"));
        String username = str(body.get("username"));
        String accessToken = str(body.get("access_token"));
        String facebookPageId = str(body.get("facebook_page_id"));
        if (igAccountId == null || igAccountId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "instagram_account_id and access_token are required.");
        }

        Long workspaceId = userDetails.getWorkspaceId();
        ChannelAccount ca = findExisting(workspaceId, "instagram", "instagram_account_id", igAccountId);
        if (ca == null) {
            ca = new ChannelAccount();
            ca.setWorkspaceId(workspaceId);
            ca.setChannel("instagram");
        }
        ca.setProvider("meta");
        ca.setStatus("active");
        ca.setDisplayName(username != null ? "@" + username : "Instagram " + igAccountId);

        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("access_token", accessToken);
        credentials.put("instagram_account_id", igAccountId);
        ca.setCredentials(writeJson(credentials));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("instagram_page_id", igAccountId);
        meta.put("instagram_account_id", igAccountId);
        if (facebookPageId != null && !facebookPageId.isBlank()) meta.put("facebook_page_id", facebookPageId);
        ca.setMetaJson(writeJson(meta));
        channelAccountRepository.save(ca);

        Inertia.flashSuccess(session, "Instagram account connected.");
        return Inertia.redirect("/app/inbox");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        ChannelAccount ca = channelAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ca.getWorkspaceId().equals(userDetails.getWorkspaceId())) {
            throw new AccessDeniedException("Access denied to requested channel account.");
        }
        channelAccountRepository.delete(ca);
        Inertia.flashSuccess(session, "Channel disconnected.");
        return Inertia.redirect("/app/inbox");
    }

    @SuppressWarnings("unchecked")
    private ChannelAccount findExisting(Long workspaceId, String channel, String metaKey, String metaValue) {
        for (ChannelAccount ca : channelAccountRepository.findByWorkspaceId(workspaceId)) {
            if (!channel.equalsIgnoreCase(ca.getChannel())) continue;
            try {
                Map<String, Object> meta = ca.getMetaJson() != null ? objectMapper.readValue(ca.getMetaJson(), Map.class) : Map.of();
                if (metaValue.equals(str(meta.get(metaKey)))) return ca;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
