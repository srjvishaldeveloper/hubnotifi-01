package com.whatsmine.inertia;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public interface GlobalPropsProvider {
    Map<String, Object> getSharedProps(HttpServletRequest request);
}
