package com.whatsmine.inertia;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

@Component
public class InertiaInterceptor implements HandlerInterceptor {

    public static final String INERTIA_HEADER = "X-Inertia";
    public static final String INERTIA_VERSION_HEADER = "X-Inertia-Version";
    public static final String INERTIA_LOCATION_HEADER = "X-Inertia-Location";
    public static final String IS_INERTIA_REQUEST_ATTR = "IS_INERTIA_REQUEST";

    private final InertiaRenderer inertiaRenderer;

    public InertiaInterceptor(InertiaRenderer inertiaRenderer) {
        this.inertiaRenderer = inertiaRenderer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String isInertiaHeader = request.getHeader(INERTIA_HEADER);
        boolean isInertiaRequest = isInertiaHeader != null && Boolean.parseBoolean(isInertiaHeader);

        if (isInertiaRequest) {
            request.setAttribute(IS_INERTIA_REQUEST_ATTR, true);
            // Deliberately NOT setting the X-Inertia response header here: if the
            // handler throws (e.g. ResponseStatusException for a 404), postHandle
            // below never runs, but a header set here would still be on the
            // response — tricking the Inertia client into parsing a plain error
            // JSON body as a page object and crashing the SPA instead of showing
            // a normal error. postHandle sets it only on successful completion.
            response.setHeader("Vary", "Accept");

            // Asset Version Check on GET requests
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String clientVersion = request.getHeader(INERTIA_VERSION_HEADER);
                String currentVersion = inertiaRenderer.getVersion();

                if (clientVersion != null && !clientVersion.equals(currentVersion)) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
                    response.setHeader(INERTIA_LOCATION_HEADER, getFullRequestUrl(request));
                    return false; // Abort request
                }
            }
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        Boolean isInertiaRequest = (Boolean) request.getAttribute(IS_INERTIA_REQUEST_ATTR);

        if (isInertiaRequest != null && isInertiaRequest) {
            response.setHeader(INERTIA_HEADER, "true");

            int status = response.getStatus();
            String method = request.getMethod();

            if (isRedirectStatus(status) && isNonGetMethod(method)) {
                response.setStatus(HttpServletResponse.SC_SEE_OTHER); // 303 See Other
            }
        }
    }

    private boolean isRedirectStatus(int status) {
        return status == HttpServletResponse.SC_MOVED_PERMANENTLY ||
               status == HttpServletResponse.SC_MOVED_TEMPORARILY ||
               status == HttpServletResponse.SC_TEMPORARY_REDIRECT ||
               status == HttpServletResponse.SC_FOUND;
    }

    private boolean isNonGetMethod(String method) {
        return "POST".equalsIgnoreCase(method) ||
               "PUT".equalsIgnoreCase(method) ||
               "PATCH".equalsIgnoreCase(method) ||
               "DELETE".equalsIgnoreCase(method);
    }

    private String getFullRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        String uri = request.getRequestURI();
        if (queryString != null && !queryString.isEmpty()) {
            return uri + "?" + queryString;
        }
        return uri;
    }
}
