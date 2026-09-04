package com.whatsmine.queue;

import java.util.Map;

public class JobPayload {
    private String uuid;
    private String jobType;
    private int maxTries;
    private int[] backoff;
    private Map<String, Object> data;

    public JobPayload() {}

    public JobPayload(String uuid, String jobType, int maxTries, int[] backoff, Map<String, Object> data) {
        this.uuid = uuid;
        this.jobType = jobType;
        this.maxTries = maxTries;
        this.backoff = backoff;
        this.data = data;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public int getMaxTries() {
        return maxTries;
    }

    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    public int[] getBackoff() {
        return backoff;
    }

    public void setBackoff(int[] backoff) {
        this.backoff = backoff;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
