package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import com.whatsmine.service.social.SocialPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Handles PublishSocialPostJob — porting PHP's Social\Jobs\PublishSocialPostJob. */
@Component
public class PublishSocialPostJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishSocialPostJobHandler.class);

    private final SocialPublisher socialPublisher;

    public PublishSocialPostJobHandler(SocialPublisher socialPublisher) {
        this.socialPublisher = socialPublisher;
    }

    @Override
    public String getJobType() {
        return "PublishSocialPostJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("postId") == null) return;
        Long postId = ((Number) data.get("postId")).longValue();
        log.info("PublishSocialPostJob: publishing post {}", postId);
        socialPublisher.publish(postId);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
