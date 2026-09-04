package com.whatsmine.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ZiggyController {

    @GetMapping(value = "/ziggy.js", produces = "application/javascript")
    public ResponseEntity<String> ziggyJs() {
        String js = """
            (function () {
                const Ziggy = {
                    url: (typeof window !== 'undefined' ? window.location.origin : "http://localhost:8080"),
                    port: (typeof window !== 'undefined' && window.location.port ? parseInt(window.location.port) : 8080),
                    defaults: {},
                    routes: {
                        "welcome": { "uri": "", "methods": ["GET", "HEAD"] },
                        "login": { "uri": "login", "methods": ["GET", "HEAD"] },
                        "register": { "uri": "register", "methods": ["GET", "HEAD"] },
                        "password.request": { "uri": "forgot-password", "methods": ["GET", "HEAD"] },
                        "password.reset": { "uri": "reset-password/{token}", "methods": ["GET", "HEAD"] },
                        "logout": { "uri": "logout", "methods": ["POST"] },
                        "dashboard": { "uri": "dashboard", "methods": ["GET", "HEAD"] },
                        "contacts.index": { "uri": "contacts", "methods": ["GET", "HEAD"] },
                        "contacts.show": { "uri": "contacts/{id}", "methods": ["GET", "HEAD"] },
                        "inbox.index": { "uri": "inbox", "methods": ["GET", "HEAD"] },
                        "inbox.show": { "uri": "inbox/conversations/{uuid}", "methods": ["GET", "HEAD"] },
                        "broadcasting.auth": { "uri": "broadcasting/auth", "methods": ["POST"] },
                        "billing": { "uri": "billing", "methods": ["GET", "HEAD"] },
                        "profile.edit": { "uri": "profile", "methods": ["GET", "HEAD"] }
                    }
                };

                if (typeof window !== 'undefined' && window.Ziggy && window.Ziggy.routes) {
                    Object.assign(Ziggy.routes, window.Ziggy.routes);
                }
                if (typeof window !== 'undefined') {
                    window.Ziggy = Ziggy;
                }

                function route(name, params, absolute = false, customZiggy = Ziggy) {
                    if (!name) {
                        return {
                            current: (currentName) => {
                                if (typeof window === 'undefined') return false;
                                const path = window.location.pathname.replace(/^\\//, '');
                                if (!currentName) return path;
                                const r = customZiggy.routes[currentName];
                                if (!r) return false;
                                return path === r.uri.replace(/^\\//, '');
                            }
                        };
                    }

                    let routeObj = customZiggy.routes[name];
                    let uri = routeObj ? routeObj.uri : name.replace(/\\./g, '/');
                    if (!uri.startsWith('/')) uri = '/' + uri;

                    if (params !== undefined && params !== null) {
                        if (typeof params === 'object' && !Array.isArray(params)) {
                            let unusedParams = Object.assign({}, params);
                            uri = uri.replace(/\\{([^}]+)\\}/g, (match, key) => {
                                const cleanKey = key.replace(/\\?$/, '');
                                if (unusedParams[cleanKey] !== undefined) {
                                    const val = unusedParams[cleanKey];
                                    delete unusedParams[cleanKey];
                                    return encodeURIComponent(val);
                                }
                                return match;
                            });
                            const queryKeys = Object.keys(unusedParams);
                            if (queryKeys.length > 0) {
                                const queryStr = queryKeys.map(k => encodeURIComponent(k) + '=' + encodeURIComponent(unusedParams[k])).join('&');
                                uri += (uri.includes('?') ? '&' : '?') + queryStr;
                            }
                        } else {
                            uri = uri.replace(/\\{([^}]+)\\}/g, encodeURIComponent(params));
                        }
                    }

                    return absolute ? (customZiggy.url + uri) : uri;
                }

                route.current = (name) => {
                    if (typeof window === 'undefined') return false;
                    const path = window.location.pathname.replace(/^\\//, '');
                    if (!name) return path;
                    const r = Ziggy.routes[name];
                    if (!r) return false;
                    return path === r.uri.replace(/^\\//, '');
                };

                if (typeof window !== 'undefined') {
                    window.route = route;
                }
                if (typeof globalThis !== 'undefined') {
                    globalThis.route = route;
                }
            })();
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/javascript");
        return new ResponseEntity<>(js, headers, HttpStatus.OK);
    }
}

