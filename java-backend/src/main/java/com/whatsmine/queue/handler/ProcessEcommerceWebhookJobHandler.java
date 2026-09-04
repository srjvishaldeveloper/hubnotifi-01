package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcessEcommerceWebhookJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessEcommerceWebhookJobHandler.class);

    @Override
    public String getJobType() {
        return "ProcessEcommerceWebhookJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number storeId = (Number) data.get("storeId");
        String topic = (String) data.get("topic");
        log.info("Executing ProcessEcommerceWebhookJob for storeId={}, topic={}", storeId != null ? storeId.longValue() : null, topic);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
