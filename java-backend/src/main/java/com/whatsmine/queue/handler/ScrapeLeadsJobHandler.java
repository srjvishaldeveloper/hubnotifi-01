package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ScrapeLeadsJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ScrapeLeadsJobHandler.class);

    @Override
    public String getJobType() {
        return "ScrapeLeadsJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number scrapeJobId = (Number) data.get("scrapeJobId");
        log.info("Executing ScrapeLeadsJob for scrapeJobId={}", scrapeJobId != null ? scrapeJobId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
