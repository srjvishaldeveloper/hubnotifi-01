package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports php's Social\Services\Drivers\InstagramSocialDriver (feed posting,
 * distinct from the WhatsApp/Messenger/Instagram-DM work done earlier this
 * session — this posts to the account's public feed, not a direct message).
 */
@Component
public class InstagramSocialDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "instagram";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        JsonNode res = SocialHttp.get("https://graph.instagram.com/me?fields=id,name,profile_picture_url&access_token=" + SocialHttp.enc(accessToken));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", res.path("id").asText(""));
        info.put("name", res.path("name").asText(""));
        info.put("picture_url", res.path("profile_picture_url").isMissingNode() ? null : res.path("profile_picture_url").asText());
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        String igUserId = account.getProviderUserId();
        String token = account.getToken();
        List<String> mediaUrls = post.getMediaUrls() != null ? post.getMediaUrls().stream().filter(u -> u != null && !u.isBlank()).toList() : List.of();
        if (mediaUrls.isEmpty()) {
            throw new IllegalStateException("Instagram posts require at least one image.");
        }

        Map<String, String> containerForm = new LinkedHashMap<>();
        containerForm.put("caption", post.getContent() != null ? post.getContent() : "");
        containerForm.put("image_url", mediaUrls.get(0));
        containerForm.put("access_token", token);
        JsonNode container = SocialHttp.postForm("https://graph.facebook.com/v19.0/" + igUserId + "/media", containerForm);
        String creationId = container.path("id").isMissingNode() ? null : container.path("id").asText();
        if (creationId == null) {
            throw new RuntimeException("Instagram container creation failed: " + container);
        }

        Map<String, String> publishForm = Map.of("creation_id", creationId, "access_token", token);
        JsonNode res = SocialHttp.postForm("https://graph.facebook.com/v19.0/" + igUserId + "/media_publish", publishForm);
        if (res.path("id").isMissingNode()) {
            throw new RuntimeException("Instagram publish failed: " + res);
        }
        return res.path("id").asText();
    }
}
