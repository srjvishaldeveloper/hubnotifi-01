package com.whatsmine.service.client;

import com.whatsmine.model.Client;
import com.whatsmine.model.ClientSubscription;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.ClientSubscriptionRepository;
import com.whatsmine.repository.SubscriptionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Java port of PHP's User::effectiveSubscription(): a client-level subscription
 * takes precedence over the user's own, falling back to the user's own
 * active/trialing subscription (checked against ends_at) when there is no client
 * or no active client subscription. Shared by DashboardController and
 * OnboardingProgressService so the precedence logic lives in exactly one place.
 */
@Component
public class EffectiveSubscriptionResolver {

    private final ClientRepository clientRepository;
    private final ClientSubscriptionRepository clientSubscriptionRepository;
    private final SubscriptionRepository subscriptionRepository;

    public EffectiveSubscriptionResolver(ClientRepository clientRepository,
                                          ClientSubscriptionRepository clientSubscriptionRepository,
                                          SubscriptionRepository subscriptionRepository) {
        this.clientRepository = clientRepository;
        this.clientSubscriptionRepository = clientSubscriptionRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public Result resolve(User user) {
        if (user.getClientId() != null) {
            Client client = clientRepository.findById(user.getClientId()).orElse(null);
            if (client != null) {
                ClientSubscription cs = clientSubscriptionRepository
                        .findFirstByClientIdAndStatusOrderByCreatedAtDesc(client.getId(), "active")
                        .orElse(null);
                if (cs != null) {
                    return new Result(cs.getPlanId(), cs.getStatus(), cs.getEndsAt(), true);
                }
            }
        }

        Subscription sub = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), List.of("active", "trialing"))
                .orElse(null);
        if (sub != null && (sub.getEndsAt() == null || sub.getEndsAt().isAfter(LocalDateTime.now()))) {
            return new Result(sub.getPlanId(), sub.getStatus(), sub.getRenewsAt(), false);
        }

        return null;
    }

    public record Result(Long planId, String status, LocalDateTime renewsOrEndsAt, boolean managedByAdmin) {}
}
