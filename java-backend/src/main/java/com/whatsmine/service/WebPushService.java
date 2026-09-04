package com.whatsmine.service;

import com.whatsmine.model.PushSubscription;
import com.whatsmine.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public WebPushService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    public boolean sendWebPush(Long userId, String title, String body) {
        List<PushSubscription> subs = pushSubscriptionRepository.findByUserId(userId);
        return !subs.isEmpty();
    }
}
