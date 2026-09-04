package com.whatsmine.queue.handler;

import com.whatsmine.model.WebhookEndpoint;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.WebhookEndpointRepository;
import com.whatsmine.service.WebhookDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class DispatchWebhookJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchWebhookJobHandler.class);

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDispatchService webhookDispatchService;

    public DispatchWebhookJobHandler(WebhookEndpointRepository webhookEndpointRepository,
                                     WebhookDispatchService webhookDispatchService) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDispatchService = webhookDispatchService;
    }

    @Override
    public String getJobType() {
        return "DispatchWebhookJob";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number endpointIdNum = (Number) data.get("endpoint_id");
        String event = (String) data.get("event");
        Map<String, Object> payload = (Map<String, Object>) data.get("payload");

        if (endpointIdNum != null && event != null) {
            log.info("Executing DispatchWebhookJob for endpointId={}, event={}", endpointIdNum.longValue(), event);
            Optional<WebhookEndpoint> endpointOpt = webhookEndpointRepository.findById(endpointIdNum.longValue());
            if (endpointOpt.isPresent()) {
                webhookDispatchService.dispatchToEndpoint(endpointOpt.get(), event, payload);
            }
        }
    }

    @Override
    public int getMaxTries() {
        return 5;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60, 300, 3600, 86400, 86400};
    }
}
