package com.whatsmine.queue.handler;

import com.whatsmine.model.SocialAccount;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.SocialAccountRepository;
import com.whatsmine.service.social.SocialOAuthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Handles RefreshSocialTokensJob: refreshes any connected account's access
 * token that's expiring soon and has a refresh token on file. Facebook/
 * Instagram tokens are long-lived (no refresh endpoint — matches PHP's own
 * exception for those two networks in OAuthManager::refresh()), so they're
 * skipped here, same as PHP.
 */
@Component
public class RefreshSocialTokensJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(RefreshSocialTokensJobHandler.class);

    private final SocialAccountRepository accountRepository;
    private final SocialOAuthManager oauthManager;

    public RefreshSocialTokensJobHandler(SocialAccountRepository accountRepository, SocialOAuthManager oauthManager) {
        this.accountRepository = accountRepository;
        this.oauthManager = oauthManager;
    }

    @Override
    public String getJobType() {
        return "RefreshSocialTokensJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        List<SocialAccount> expiringSoon = accountRepository.findByRefreshTokenIsNotNullAndExpiresAtBefore(LocalDateTime.now().plusDays(1));

        int refreshed = 0;
        for (SocialAccount account : expiringSoon) {
            if ("facebook".equals(account.getProvider()) || "instagram".equals(account.getProvider())) {
                continue; // long-lived tokens; no refresh endpoint
            }
            try {
                Map<String, Object> tokens = oauthManager.refresh(account.getProvider(), account.getRefreshToken());
                String accessToken = tokens.get("access_token") != null ? String.valueOf(tokens.get("access_token")) : null;
                if (accessToken == null) continue;

                account.setToken(accessToken);
                if (tokens.get("refresh_token") != null) {
                    account.setRefreshToken(String.valueOf(tokens.get("refresh_token")));
                }
                Object expiresIn = tokens.get("expires_in");
                account.setExpiresAt(expiresIn instanceof Number n ? LocalDateTime.now().plusSeconds(n.longValue()) : null);
                accountRepository.save(account);
                refreshed++;
            } catch (Exception e) {
                log.warn("RefreshSocialTokensJob: failed to refresh account {} ({}): {}", account.getId(), account.getProvider(), e.getMessage());
            }
        }

        if (refreshed > 0) {
            log.info("RefreshSocialTokensJob: refreshed {} account token(s).", refreshed);
        }
    }

    @Override
    public int getMaxTries() {
        return 1;
    }
}
