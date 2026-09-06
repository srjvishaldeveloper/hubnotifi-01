package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ports php's Social\Services\Drivers\LinkedInDriver (UGC Posts API). */
@Component
public class LinkedInDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "linkedin";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        JsonNode res = SocialHttp.getWithBearer(
                "https://api.linkedin.com/v2/me?projection=(id,localizedFirstName,localizedLastName,profilePicture(displayImage~:playableStreams))",
                accessToken);

        JsonNode elements = res.path("profilePicture").path("displayImage~").path("elements");
        String pictureUrl = null;
        if (elements.isArray() && elements.size() > 0) {
            JsonNode identifiers = elements.get(0).path("identifiers");
            if (identifiers.isArray() && identifiers.size() > 0) {
                pictureUrl = identifiers.get(0).path("identifier").asText(null);
            }
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", res.path("id").asText(""));
        info.put("name", (res.path("localizedFirstName").asText("") + " " + res.path("localizedLastName").asText("")).trim());
        info.put("picture_url", pictureUrl);
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        String urn = "urn:li:person:" + account.getProviderUserId();

        Map<String, Object> shareContent = new LinkedHashMap<>();
        shareContent.put("shareCommentary", Map.of("text", post.getContent() != null ? post.getContent() : ""));
        shareContent.put("shareMediaCategory", "NONE");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("author", urn);
        body.put("lifecycleState", "PUBLISHED");
        body.put("specificContent", Map.of("com.linkedin.ugc.ShareContent", shareContent));
        body.put("visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

        JsonNode res = SocialHttp.postJsonWithBearer("https://api.linkedin.com/v2/ugcPosts", body, account.getToken());
        if (res.path("id").isMissingNode()) {
            throw new RuntimeException("LinkedIn publish failed: " + res);
        }
        return res.path("id").asText();
    }
}
