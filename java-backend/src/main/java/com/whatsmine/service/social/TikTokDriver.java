package com.whatsmine.service.social;

import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth connection is real (see SocialOAuthManager), but publishing requires
 * TikTok's Content Posting API, which needs a video file upload (not a
 * text/image "post") plus app audit approval before it can publish content
 * that isn't self-view-only. Honestly reporting that rather than fabricating
 * a publish, matching this session's pattern for GenericGateway/unsupported
 * SMS providers/etc.
 */
@Component
public class TikTokDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "tiktok";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        var res = SocialHttp.getWithBearer("https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,avatar_url", accessToken);
        var user = res.path("data").path("user");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", user.path("open_id").asText(""));
        info.put("name", user.path("display_name").asText(""));
        info.put("picture_url", user.path("avatar_url").isMissingNode() ? null : user.path("avatar_url").asText());
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        throw new UnsupportedOperationException(
                "Publishing to TikTok isn't supported — it requires uploading a video file via TikTok's Content Posting API plus app audit approval, not a text/image post.");
    }
}
