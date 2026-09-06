package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ports php's Social\Services\Drivers\FacebookDriver (Page feed posting). */
@Component
public class FacebookDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "facebook";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        JsonNode res = SocialHttp.get("https://graph.facebook.com/v19.0/me?fields=id,name,picture&access_token=" + SocialHttp.enc(accessToken));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", res.path("id").asText(""));
        info.put("name", res.path("name").asText(""));
        info.put("picture_url", res.path("picture").path("data").path("url").asText(null));
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        String pageId = account.getProviderUserId();
        String token = account.getToken();
        String message = post.getContent() != null ? post.getContent() : "";
        List<String> mediaUrls = post.getMediaUrls() != null ? post.getMediaUrls().stream().filter(u -> u != null && !u.isBlank()).toList() : List.of();

        if (mediaUrls.size() == 1) {
            Map<String, String> form = Map.of("url", mediaUrls.get(0), "caption", message, "access_token", token);
            JsonNode res = SocialHttp.postForm("https://graph.facebook.com/v19.0/" + pageId + "/photos", form);
            if (res.path("id").isMissingNode()) {
                throw new RuntimeException("Facebook photo publish failed: " + res);
            }
            return res.path("post_id").isMissingNode() ? res.path("id").asText() : res.path("post_id").asText();
        }

        if (mediaUrls.size() > 1) {
            List<Map<String, Object>> attached = new java.util.ArrayList<>();
            for (String url : mediaUrls) {
                Map<String, String> uploadForm = Map.of("url", url, "published", "false", "access_token", token);
                JsonNode upload = SocialHttp.postForm("https://graph.facebook.com/v19.0/" + pageId + "/photos", uploadForm);
                if (upload.path("id").isMissingNode()) {
                    throw new RuntimeException("Facebook photo upload failed: " + upload);
                }
                attached.add(Map.of("media_fbid", upload.path("id").asText()));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", message);
            body.put("attached_media", attached);
            body.put("access_token", token);
            JsonNode res = SocialHttp.postJsonWithBearer("https://graph.facebook.com/v19.0/" + pageId + "/feed?access_token=" + SocialHttp.enc(token), body, token);
            if (res.path("id").isMissingNode()) {
                throw new RuntimeException("Facebook multi-photo publish failed: " + res);
            }
            return res.path("id").asText();
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("message", message);
        form.put("access_token", token);
        JsonNode res = SocialHttp.postForm("https://graph.facebook.com/v19.0/" + pageId + "/feed", form);
        if (res.path("id").isMissingNode()) {
            throw new RuntimeException("Facebook publish failed: " + res);
        }
        return res.path("id").asText();
    }
}
