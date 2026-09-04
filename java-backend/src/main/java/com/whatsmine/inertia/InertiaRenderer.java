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

            String headAssetTags;
            java.io.File hotFile = new java.io.File("../php/public/hot");
            if (!hotFile.exists()) {
                hotFile = new java.io.File("public/hot");
            }

            if (hotFile.exists()) {
                String hotUrl = "http://127.0.0.1:5173";
                try {
                    String content = java.nio.file.Files.readString(hotFile.toPath()).trim();
                    if (!content.isEmpty()) {
                        hotUrl = content;
                    }
                } catch (Exception ignored) {}
                headAssetTags = """
                    <script type="module">
                        import RefreshRuntime from "%s/@react-refresh";
                        RefreshRuntime.injectIntoGlobalHook(window);
                        window.$RefreshReg$ = () => {};
                        window.$RefreshSig$ = () => (type) => type;
                        window.__vite_plugin_react_preamble_installed__ = true;
                    </script>
                    <script type="module" src="%s/@vite/client"></script>
                    <script type="module" src="%s/resources/js/app.jsx"></script>
                    """.formatted(hotUrl, hotUrl, hotUrl);
            } else {
                String jsFile = "assets/app-wcd0bkdE.js";
                String cssFile = "assets/app-BMT4SSt3.css";
                try {
                    String manifestContent = null;
                    try (var is = getClass().getResourceAsStream("/static/build/manifest.json")) {
                        if (is != null) {
                            manifestContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } catch (Exception ignored) {}

                    if (manifestContent == null) {
                        java.io.File manifestFile = new java.io.File("src/main/resources/static/build/manifest.json");
                        if (!manifestFile.exists()) {
                            manifestFile = new java.io.File("build/resources/main/static/build/manifest.json");
                        }
                        if (!manifestFile.exists()) {
                            manifestFile = new java.io.File("../php/public/build/manifest.json");
                        }
                        if (manifestFile.exists()) {
                            manifestContent = java.nio.file.Files.readString(manifestFile.toPath());
                        }
                    }

                    if (manifestContent != null) {
                        var jsonNode = objectMapper.readTree(manifestContent);
                        var entryNode = jsonNode.get("resources/js/app.jsx");
                        if (entryNode != null) {
                            if (entryNode.has("file")) {
                                jsFile = entryNode.get("file").asText();
                            }
                            if (entryNode.has("css") && entryNode.get("css").isArray() && entryNode.get("css").size() > 0) {
                                cssFile = entryNode.get("css").get(0).asText();
                            }
                        }
                    }
                } catch (Exception ignored) {}

                headAssetTags = """
                    <link rel="stylesheet" href="/build/%s">
                    <script type="module" src="/build/%s"></script>
                    """.formatted(cssFile, jsFile);
            }

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
                    %s
                </head>
                <body class="font-sans antialiased">
                    <div id="app" data-page="%s"></div>
                </body>
                </html>
                """.formatted(
                    extractCsrfToken(inertiaResponse),
                    inertiaResponse.getComponent(),
                    headAssetTags,
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
