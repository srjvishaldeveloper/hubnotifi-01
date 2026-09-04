package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import com.whatsmine.realtime.RealtimeBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BroadcastEventJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(BroadcastEventJobHandler.class);
    private final RealtimeBroadcaster realtimeBroadcaster;

    public BroadcastEventJobHandler(RealtimeBroadcaster realtimeBroadcaster) {
        this.realtimeBroadcaster = realtimeBroadcaster;
    }

    @Override
    public String getJobType() {
        return "BroadcastEventJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        String channel = (String) data.get("channel");
        String event = (String) data.get("event");
        Object payload = data.get("payload");

        if (channel == null || event == null) {
            log.warn("BroadcastEventJob missing channel or event in payload: {}", data);
            return;
        }

        log.info("Processing BroadcastEventJob for event {} on channel {}", event, channel);
        realtimeBroadcaster.broadcast(channel, event, payload);
    }
}
