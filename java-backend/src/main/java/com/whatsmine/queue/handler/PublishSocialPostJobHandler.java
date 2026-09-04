package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PublishSocialPostJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishSocialPostJobHandler.class);

    @Override
    public String getJobType() {
        return "PublishSocialPostJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number postId = (Number) data.get("postId");
        log.info("Executing PublishSocialPostJob for postId={}", postId != null ? postId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
