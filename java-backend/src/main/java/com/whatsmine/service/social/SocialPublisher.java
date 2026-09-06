package com.whatsmine.service.social;

import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import com.whatsmine.model.SocialPostAccount;
import com.whatsmine.repository.SocialAccountRepository;
import com.whatsmine.repository.SocialPostAccountRepository;
import com.whatsmine.repository.SocialPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports php's Social\Services\SocialPublisher: publishes a post to every
 * account already linked to it via SocialPostAccount (populated by the
 * composer at creation time — Java's schema normalizes target accounts
 * into that join table rather than PHP's target_accounts JSON column on
 * the post itself).
 */
@Service
public class SocialPublisher {

    private static final Logger log = LoggerFactory.getLogger(SocialPublisher.class);

    private final SocialPostRepository postRepository;
    private final SocialAccountRepository accountRepository;
    private final SocialPostAccountRepository postAccountRepository;
    private final Map<String, SocialNetworkDriver> drivers;

    public SocialPublisher(SocialPostRepository postRepository,
                            SocialAccountRepository accountRepository,
                            SocialPostAccountRepository postAccountRepository,
                            List<SocialNetworkDriver> driverList) {
        this.postRepository = postRepository;
        this.accountRepository = accountRepository;
        this.postAccountRepository = postAccountRepository;
        this.drivers = new LinkedHashMap<>();
        for (SocialNetworkDriver driver : driverList) {
            drivers.put(driver.network(), driver);
        }
    }

    @Transactional
    public void publish(Long postId) {
        SocialPost post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        post.setStatus("publishing");
        postRepository.save(post);

        List<SocialPostAccount> links = postAccountRepository.findBySocialPostId(postId);
        int succeeded = 0;

        for (SocialPostAccount link : links) {
            if ("published".equals(link.getStatus())) {
                succeeded++;
                continue;
            }

            SocialAccount account = accountRepository.findById(link.getSocialAccountId()).orElse(null);
            if (account == null || !account.getWorkspaceId().equals(post.getWorkspaceId())) {
                link.setStatus("failed");
                link.setError("Account not found or does not belong to this workspace.");
                postAccountRepository.save(link);
                continue;
            }

            SocialNetworkDriver driver = drivers.get(account.getProvider());
            if (driver == null) {
                link.setStatus("failed");
                link.setError("No driver for network " + account.getProvider() + ".");
                postAccountRepository.save(link);
                continue;
            }

            try {
                String platformId = driver.publish(account, post);
                link.setStatus("published");
                link.setPostIdExternal(platformId);
                link.setError(null);
                postAccountRepository.save(link);
                succeeded++;
            } catch (Exception e) {
                log.error("Social publish failed post={} account={} network={}: {}", postId, account.getId(), account.getProvider(), e.getMessage());
                link.setStatus("failed");
                link.setError("Publish failed: " + e.getMessage());
                postAccountRepository.save(link);
            }
        }

        boolean allFailed = succeeded == 0;
        post.setStatus(allFailed ? "failed" : "published");
        post.setPublishedAt(allFailed ? null : LocalDateTime.now());
        postRepository.save(post);
    }
}
