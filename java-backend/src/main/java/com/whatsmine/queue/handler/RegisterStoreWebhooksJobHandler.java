package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RegisterStoreWebhooksJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(RegisterStoreWebhooksJobHandler.class);

    @Override
    public String getJobType() {
        return "RegisterStoreWebhooksJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number storeId = (Number) data.get("storeId");
        log.info("Executing RegisterStoreWebhooksJob for storeId={}", storeId != null ? storeId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
