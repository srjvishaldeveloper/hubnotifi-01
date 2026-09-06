package com.whatsmine.controller.social;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.repository.SocialAccountRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.social.SocialNetworkDriver;
import com.whatsmine.service.social.SocialOAuthManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connect/list/disconnect social accounts via real OAuth, porting PHP's
 * Social\Http\Controllers\SocialAccountController. Renders the existing
 * Social/Accounts/Index.jsx page unmodified.
 */
@RestController
@RequestMapping("/app/social/accounts")
public class SocialAccountController {

    private static final Set<String> VALID_NETWORKS = Set.of("facebook", "instagram", "linkedin", "twitter", "youtube", "tiktok");
    private static final String SESSION_KEY = "social_oauth";

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private SocialAccountRepository accountRepository;

    @Autowired
    private SocialOAuthManager oauthManager;

    @Autowired
    private List<SocialNetworkDriver> driverList;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<SocialAccount> accounts = accountRepository.findByWorkspaceId(workspaceId);
        return inertiaRenderer.render("Social/Accounts/Index", Map.of("accounts", accounts.stream().map(this::accountRow).toList()), request);
    }

    @GetMapping("/connect/{network}")
    public Object connect(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String network,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        if (!VALID_NETWORKS.contains(network)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String state = oauthManager.generateState();
        String verifier = "twitter".equals(network) ? oauthManager.generatePkceVerifier() : null;

        Map<String, Object> oauthState = new LinkedHashMap<>();
        oauthState.put("state", state);
        oauthState.put("network", network);
        oauthState.put("workspace", workspaceId);
        if (verifier != null) oauthState.put("verifier", verifier);
        session.setAttribute(SESSION_KEY, oauthState);

        String callbackUrl = appUrl + "/app/social/accounts/callback/" + network;
        try {
            String url = oauthManager.getAuthUrl(network, state, verifier, callbackUrl);
            return Inertia.location(url);
        } catch (IllegalStateException e) {
            Inertia.flashError(session, "OAuth for " + network + " is not configured. Please contact your administrator.");
            return Inertia.redirect("/app/social/accounts");
        }
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/callback/{network}")
    public Object callback(
            @PathVariable String network,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session
    ) {
        Map<String, Object> stored = (Map<String, Object>) session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);

        if (error != null || code == null || code.isBlank()) {
            Inertia.flashError(session, "OAuth failed: " + (error != null ? error : "No code received"));
            return Inertia.redirect("/app/social/accounts");
        }

        String storedState = stored != null ? String.valueOf(stored.get("state")) : null;
        if (storedState == null || !java.security.MessageDigest.isEqual(storedState.getBytes(), state != null ? state.getBytes() : new byte[0])) {
            Inertia.flashError(session, "Invalid OAuth state. Please try connecting again.");
            return Inertia.redirect("/app/social/accounts");
        }

        Long workspaceId = stored.get("workspace") instanceof Number n ? n.longValue() : null;
        String verifier = stored.get("verifier") != null ? String.valueOf(stored.get("verifier")) : null;
        String callbackUrl = appUrl + "/app/social/accounts/callback/" + network;

        Map<String, Object> tokens;
        try {
            tokens = oauthManager.exchangeCode(network, code, callbackUrl, verifier);
        } catch (Exception e) {
            Inertia.flashError(session, "Failed to obtain access token: " + e.getMessage());
            return Inertia.redirect("/app/social/accounts");
        }

        String accessToken = tokens.get("access_token") != null ? String.valueOf(tokens.get("access_token")) : null;
        if (accessToken == null || accessToken.isBlank()) {
            Inertia.flashError(session, "Failed to obtain access token.");
            return Inertia.redirect("/app/social/accounts");
        }

        SocialNetworkDriver driver = driverList.stream().filter(d -> d.network().equals(network)).findFirst().orElse(null);
        Map<String, Object> accountInfo;
        try {
            accountInfo = driver != null ? driver.fetchAccountInfo(accessToken) : Map.of("account_id", "", "name", "", "picture_url", null);
        } catch (Exception e) {
            Inertia.flashError(session, "Connected but could not fetch account info: " + e.getMessage());
            return Inertia.redirect("/app/social/accounts");
        }

        String accountId = String.valueOf(accountInfo.getOrDefault("account_id", ""));
        SocialAccount account = accountRepository.findByWorkspaceIdAndProviderAndProviderUserId(workspaceId, network, accountId)
                .orElseGet(() -> {
                    SocialAccount a = new SocialAccount();
                    a.setWorkspaceId(workspaceId);
                    a.setProvider(network);
                    a.setProviderUserId(accountId);
                    return a;
                });
        account.setName(String.valueOf(accountInfo.getOrDefault("name", "")));
        account.setPictureUrl((String) accountInfo.get("picture_url"));
        account.setToken(accessToken);
        account.setRefreshToken(tokens.get("refresh_token") != null ? String.valueOf(tokens.get("refresh_token")) : null);
        Object expiresIn = tokens.get("expires_in");
        account.setExpiresAt(expiresIn instanceof Number n ? LocalDateTime.now().plusSeconds(n.longValue()) : null);
        account.setActive(true);
        accountRepository.save(account);

        Inertia.flashSuccess(session, capitalize(network) + " account connected.");
        return Inertia.redirect("/app/social/accounts");
    }

    @DeleteMapping("/{id}")
    public Object disconnect(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialAccount account = accountRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        accountRepository.delete(account);
        Inertia.flashSuccess(session, "Account disconnected.");
        return Inertia.redirect("/app/social/accounts");
    }

    private Map<String, Object> accountRow(SocialAccount a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("network", a.getProvider());
        row.put("name", a.getName());
        row.put("picture_url", a.getPictureUrl());
        row.put("token_expires_at", a.getExpiresAt());
        return row;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
