package com.whatsmine.service;

import com.whatsmine.model.PushSubscription;
import com.whatsmine.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OneSignalService {

    public boolean sendPushNotification(String userExternalId, String title, String message) {
        // Stub / Mock integration for OneSignal dispatches
        return true;
    }
}
