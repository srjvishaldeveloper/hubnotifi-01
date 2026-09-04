package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CheckAbandonedCartJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckAbandonedCartJobHandler.class);

    @Override
    public String getJobType() {
        return "CheckAbandonedCartJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number cartId = (Number) data.get("cartId");
        log.info("Executing CheckAbandonedCartJob for cartId={}", cartId != null ? cartId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
