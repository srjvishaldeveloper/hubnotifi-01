package com.whatsmine.controller.broadcasting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.SystemSettingRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@RestController
public class BroadcastingAuthController {

    private static final Logger log = LoggerFactory.getLogger(BroadcastingAuthController.class);

    private final WorkspaceUserRepository workspaceUserRepository;
    private final ConversationRepository conversationRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    public BroadcastingAuthController(
            WorkspaceUserRepository workspaceUserRepository,
            ConversationRepository conversationRepository,
            SystemSettingRepository systemSettingRepository,
            ObjectMapper objectMapper) {
        this.workspaceUserRepository = workspaceUserRepository;
        this.conversationRepository = conversationRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/broadcasting/auth", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> authenticate(
            @RequestParam("socket_id") String socketId,
            @RequestParam("channel_name") String channelName,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            log.warn("Broadcasting auth failed: user unauthenticated for channel {}", channelName);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
        }

        Long userId = userDetails.getId();
        boolean isAuthorized = false;
        String channelDataJson = null;

        String rawChannelName = channelName;
        String normalizedChannel = channelName;
        if (normalizedChannel.startsWith("private-")) {
            normalizedChannel = normalizedChannel.substring(8);
        } else if (normalizedChannel.startsWith("presence-")) {
            normalizedChannel = normalizedChannel.substring(9);
        }

        if (normalizedChannel.startsWith("App.Models.User.")) {
            String idStr = normalizedChannel.substring("App.Models.User.".length());
            try {
                Long targetUserId = Long.parseLong(idStr);
                isAuthorized = userId.equals(targetUserId);
            } catch (NumberFormatException e) {
                isAuthorized = false;
            }
        } else if (normalizedChannel.startsWith("workspace.")) {
            String wsIdStr = normalizedChannel.substring("workspace.".length());
            try {
                Long workspaceId = Long.parseLong(wsIdStr);
                isAuthorized = userCanAccessWorkspace(userId, userDetails.getWorkspaceId(), workspaceId);
            } catch (NumberFormatException e) {
                isAuthorized = false;
            }
        } else if (normalizedChannel.startsWith("conversation.")) {
            String convIdStr = normalizedChannel.substring("conversation.".length());
            try {
                Long conversationId = Long.parseLong(convIdStr);
                Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
                if (convOpt.isPresent()) {
                    Long workspaceId = convOpt.get().getWorkspaceId();
                    isAuthorized = userCanAccessWorkspace(userId, userDetails.getWorkspaceId(), workspaceId);
                }
            } catch (NumberFormatException e) {
                isAuthorized = false;
            }
        } else {
            log.warn("Broadcasting auth denied: unknown channel pattern {}", channelName);
        }

        if (!isAuthorized) {
            log.warn("Broadcasting auth denied for user {} on channel {}", userId, channelName);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        // Build presence channel data if applicable
        if (rawChannelName.startsWith("presence-")) {
            String userName = userDetails.getUser() != null && userDetails.getUser().getName() != null
                    ? userDetails.getUser().getName()
                    : userDetails.getUsername();

            Map<String, Object> presenceInfo = Map.of(
                    "user_id", userId,
                    "user_info", Map.of(
                            "id", userId,
                            "name", userName,
                            "avatar", ""
                    )
            );
            try {
                channelDataJson = objectMapper.writeValueAsString(presenceInfo);
            } catch (Exception e) {
                log.error("Failed to serialize presence channel data", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        String appKey = getSettingValue("pusher_app_key", "pusher_key");
        String secret = getSettingValue("pusher_app_secret", "pusher_secret");

        String signatureString = channelDataJson != null
                ? socketId + ":" + rawChannelName + ":" + channelDataJson
                : socketId + ":" + rawChannelName;

        String signature = hmacSha256(signatureString, secret);
        String authHeader = appKey + ":" + signature;

        if (channelDataJson != null) {
            return ResponseEntity.ok(Map.of(
                    "auth", authHeader,
                    "channel_data", channelDataJson
            ));
        }

        return ResponseEntity.ok(Map.of("auth", authHeader));
    }

    private String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElseGet(() -> System.getenv(key.toUpperCase()) != null ? System.getenv(key.toUpperCase()) : defaultValue);
    }

    private boolean userCanAccessWorkspace(Long userId, Long primaryWorkspaceId, Long requestedWorkspaceId) {
        if (primaryWorkspaceId != null && primaryWorkspaceId.equals(requestedWorkspaceId)) {
            return true;
        }
        Optional<WorkspaceUser> membership = workspaceUserRepository.findByWorkspaceIdAndUserId(requestedWorkspaceId, userId);
        return membership.isPresent();
    }

    public static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC SHA-256 signature", e);
        }
    }
}
