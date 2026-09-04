package com.whatsmine.inertia;

import java.util.HashMap;
import java.util.Map;

public class InertiaResponse {
    private String component;
    private Map<String, Object> props;
    private String url;
    private String version;

    public InertiaResponse() {
        this.props = new HashMap<>();
    }

    public InertiaResponse(String component, Map<String, Object> props, String url, String version) {
        this.component = component;
        this.props = props != null ? props : new HashMap<>();
        this.url = url;
        this.version = version;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public InertiaResponse withProp(String key, Object value) {
        if (this.props == null) {
            this.props = new HashMap<>();
        }
        this.props.put(key, value);
        return this;
    }
}
