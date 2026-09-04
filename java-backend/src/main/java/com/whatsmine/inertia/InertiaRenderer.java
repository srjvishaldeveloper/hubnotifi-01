package com.whatsmine.inertia;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class InertiaRenderer {

    private final GlobalPropsProvider globalPropsProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.version:1.0.0}")
    private String version;

    public InertiaRenderer(GlobalPropsProvider globalPropsProvider, ObjectMapper objectMapper) {
        this.globalPropsProvider = globalPropsProvider;
        this.objectMapper = objectMapper;
    }

    public String getVersion() {
        return version;
    }

    public InertiaResponse render(String component, Map<String, Object> pageProps, HttpServletRequest request) {
        Map<String, Object> mergedProps = new HashMap<>();

        // 1. Add global shared props
        if (globalPropsProvider != null) {
            mergedProps.putAll(globalPropsProvider.getSharedProps(request));
        }

        // 2. Merge component-specific props
        if (pageProps != null) {
            mergedProps.putAll(pageProps);
        }

        // 3. Handle Partial Reloads (X-Inertia-Partial-Component & X-Inertia-Partial-Data)
        String partialComponent = request.getHeader("X-Inertia-Partial-Component");
        String partialData = request.getHeader("X-Inertia-Partial-Data");

        if (partialComponent != null && partialComponent.equals(component) && partialData != null) {
            Set<String> requestedKeys = new HashSet<>(Arrays.asList(partialData.split(",")));
            Map<String, Object> filteredProps = new HashMap<>();
            for (String key : requestedKeys) {
                String trimmedKey = key.trim();
                if (mergedProps.containsKey(trimmedKey)) {
                    filteredProps.put(trimmedKey, mergedProps.get(trimmedKey));
                }
            }
            mergedProps = filteredProps;
        }

        String requestUrl = getRequestUrl(request);
        return new InertiaResponse(component, mergedProps, requestUrl, version);
    }

    public String renderHtmlBootstrap(InertiaResponse inertiaResponse) {
        try {
            String jsonPage = objectMapper.writeValueAsString(inertiaResponse);
            String escapedJson = jsonPage
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");

            return """
                <!DOCTYPE html>
                <html lang="en" dir="ltr">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <meta name="csrf-token" content="%s">
                    <title>%s - WhatsMine</title>
                    <link rel="icon" type="image/svg+xml" href="/whatsmine-icon.svg">
                    <link rel="preconnect" href="https://fonts.bunny.net">
                    <link href="https://fonts.bunny.net/css?family=space-grotesk:400,500,600,700&display=swap" rel="stylesheet" />
                    <script src="/api/ziggy.js"></script>
                    <script type="module" src="/build/assets/app.js"></script>
                    <link rel="stylesheet" href="/build/assets/app.css">
                </head>
                <body class="font-sans antialiased">
                    <div id="app" data-page="%s"></div>
                </body>
                </html>
                """.formatted(
                    extractCsrfToken(inertiaResponse),
                    inertiaResponse.getComponent(),
                    escapedJson
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to render Inertia HTML bootstrap", e);
        }
    }

    private String extractCsrfToken(InertiaResponse response) {
        if (response.getProps() != null && response.getProps().containsKey("csrf_token")) {
            Object token = response.getProps().get("csrf_token");
            return token != null ? token.toString() : "";
        }
        return "";
    }

    private String getRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        String uri = request.getRequestURI();
        if (queryString != null && !queryString.isEmpty()) {
            return uri + "?" + queryString;
        }
        return uri;
    }
}
