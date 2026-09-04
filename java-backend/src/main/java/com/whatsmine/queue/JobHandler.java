package com.whatsmine.queue;

import java.util.Map;

public interface JobHandler {
    String getJobType();
    void handle(Map<String, Object> data) throws Exception;
    default int getMaxTries() { return 3; }
    default int[] getBackoff() { return new int[]{60}; }
}
