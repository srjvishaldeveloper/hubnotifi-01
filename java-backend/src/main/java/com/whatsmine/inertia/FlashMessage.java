package com.whatsmine.inertia;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FlashMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String success;
    private String error;
    private String warning;
    private String info;
    private Map<String, Object> extra;

    public FlashMessage() {
        this.extra = new HashMap<>();
    }

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("error", error);
        map.put("warning", warning);
        map.put("info", info);
        if (extra != null) {
            map.putAll(extra);
        }
        return map;
    }
}
