package com.whatsmine.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class QueuedNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(QueuedNotificationDispatcher.class);

    @Async
    public void sendQueuedNotification(String notificationType, Long recipientUserId, Map<String, Object> data) {
        log.info("Async notification [{}] dispatched for userId={}", notificationType, recipientUserId);
        // Async execution of mail + database notification delivery logic
    }
}
