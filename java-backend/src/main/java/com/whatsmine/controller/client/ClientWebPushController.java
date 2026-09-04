package com.whatsmine.controller.client;

import com.whatsmine.model.PushSubscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PushSubscriptionRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webpush")
public class ClientWebPushController {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public ClientWebPushController(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                         @RequestBody Map<String, Object> payload) {
        User user = userDetails.getUser();
        String endpoint = (String) payload.get("endpoint");
        String p256dh = (String) payload.get("p256dh");
        String auth = (String) payload.get("auth");
        String ua = (String) payload.get("ua");

        if (endpoint != null) {
            PushSubscription sub = pushSubscriptionRepository.findByUserIdAndEndpoint(user.getId(), endpoint)
                    .orElseGet(() -> {
                        PushSubscription ps = new PushSubscription();
                        ps.setUserId(user.getId());
                        ps.setEndpoint(endpoint);
                        return ps;
                    });
            sub.setP256dhKey(p256dh != null ? p256dh : "");
            sub.setAuthKey(auth != null ? auth : "");
            sub.setUa(ua);
            pushSubscriptionRepository.save(sub);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/unsubscribe")
    @Transactional
    public ResponseEntity<Map<String, Object>> unsubscribe(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                           @RequestBody Map<String, Object> payload) {
        User user = userDetails.getUser();
        String endpoint = (String) payload.get("endpoint");
        if (endpoint != null) {
            pushSubscriptionRepository.deleteByUserIdAndEndpoint(user.getId(), endpoint);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
