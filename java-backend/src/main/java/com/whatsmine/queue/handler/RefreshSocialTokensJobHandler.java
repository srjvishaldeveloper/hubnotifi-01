package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RefreshSocialTokensJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(RefreshSocialTokensJobHandler.class);

    @Override
    public String getJobType() {
        return "RefreshSocialTokensJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        log.info("Executing RefreshSocialTokensJob");
    }

    @Override
    public int getMaxTries() {
        return 1;
    }
}
