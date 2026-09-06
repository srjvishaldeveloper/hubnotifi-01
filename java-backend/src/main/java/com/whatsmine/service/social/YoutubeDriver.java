package com.whatsmine.service.social;

import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth connection is real (see SocialOAuthManager), but publishing a
 * text/image "post" isn't a real YouTube Data API v3 capability — YouTube's
 * public API is for video uploads, not the community-post-style updates
 * this composer produces, and YouTube's actual Community Posts capability
 * requires special channel eligibility with no stable public API. Honestly
 * reporting that rather than fabricating a publish, matching this session's
 * pattern for GenericGateway/unsupported SMS providers/etc.
 */
@Component
public class YoutubeDriver implements SocialNetworkDriver {

    @Override
    public String network() {
        return "youtube";
    }

    @Override
    public Map<String, Object> fetchAccountInfo(String accessToken) throws Exception {
        var res = SocialHttp.getWithBearer("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true", accessToken);
        var item = res.path("items").isArray() && res.path("items").size() > 0 ? res.path("items").get(0) : null;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("account_id", item != null ? item.path("id").asText("") : "");
        info.put("name", item != null ? item.path("snippet").path("title").asText("") : "");
        info.put("picture_url", item != null ? item.path("snippet").path("thumbnails").path("default").path("url").asText(null) : null);
        return info;
    }

    @Override
    public String publish(SocialAccount account, SocialPost post) throws Exception {
        throw new UnsupportedOperationException(
                "Publishing text/image posts to YouTube isn't supported — YouTube's public API is for video uploads, not community-style posts.");
    }
}
