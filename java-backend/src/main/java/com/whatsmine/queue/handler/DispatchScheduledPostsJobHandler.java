package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DispatchScheduledPostsJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduledPostsJobHandler.class);

    @Override
    public String getJobType() {
        return "DispatchScheduledPostsJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        log.info("Executing DispatchScheduledPostsJob");
    }

    @Override
    public int getMaxTries() {
        return 1;
    }
}
