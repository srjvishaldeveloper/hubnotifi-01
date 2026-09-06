package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ports php's Social\Services\Drivers\TwitterDriver (X API v2). */
@Component
public class TwitterDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "twitter";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        JsonNode res = SocialHttp.getWithBearer("https://api.twitter.com/2/users/me?user.fields=profile_image_url,name", accessToken);
        JsonNode user = res.path("data");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", user.path("id").asText(""));
        info.put("name", user.path("name").asText(""));
        info.put("picture_url", user.path("profile_image_url").isMissingNode() ? null : user.path("profile_image_url").asText());
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        String text = post.getContent() != null ? post.getContent() : "";
        if (text.length() > 280) text = text.substring(0, 280);

        JsonNode res = SocialHttp.postJsonWithBearer("https://api.twitter.com/2/tweets", Map.of("text", text), account.getToken());
        JsonNode id = res.path("data").path("id");
        if (id.isMissingNode()) {
            throw new RuntimeException("Twitter publish failed: " + res);
        }
        return id.asText();
    }
}
