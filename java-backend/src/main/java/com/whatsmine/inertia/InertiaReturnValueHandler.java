package com.whatsmine.inertia;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

public class InertiaReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final InertiaRenderer inertiaRenderer;
    private final ObjectMapper objectMapper;

    public InertiaReturnValueHandler(InertiaRenderer inertiaRenderer, ObjectMapper objectMapper) {
        this.inertiaRenderer = inertiaRenderer;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return InertiaResponse.class.isAssignableFrom(paramType) ||
               Inertia.InertiaRedirect.class.isAssignableFrom(paramType) ||
               Inertia.InertiaLocation.class.isAssignableFrom(paramType) ||
               Object.class.equals(paramType);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType,
                                  ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        if (returnValue instanceof ResponseEntity) {
            return;
        }
        mavContainer.setRequestHandled(true);

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        if (request == null || response == null) {
            return;
        }

        // 1. Handle Inertia.location(url) hard browser refresh redirect
        if (returnValue instanceof Inertia.InertiaLocation location) {
            response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict per spec
            response.setHeader("X-Inertia-Location", location.getUrl());
            return;
        }

        // 2. Handle Inertia.redirect(url) client-side SPA redirect
        if (returnValue instanceof Inertia.InertiaRedirect redirect) {
            String method = request.getMethod();
            boolean isNonGet = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) ||
                               "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);

            int status = isNonGet ? HttpServletResponse.SC_SEE_OTHER : HttpServletResponse.SC_MOVED_TEMPORARILY; // 303 for non-GET, 302 for GET
            response.setStatus(status);
            response.setHeader("Location", redirect.getUrl());
            response.setHeader("X-Inertia", "true");
            return;
        }

        // 3. Handle InertiaResponse component rendering
        if (returnValue instanceof InertiaResponse rawResponse) {
            InertiaResponse fullResponse = inertiaRenderer.render(
                    rawResponse.getComponent(),
                    rawResponse.getProps(),
                    request
            );

            String isInertiaHeader = request.getHeader("X-Inertia");
            boolean isInertiaRequest = isInertiaHeader != null && Boolean.parseBoolean(isInertiaHeader);

            if (isInertiaRequest) {
                // AJAX Inertia Response
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("X-Inertia", "true");
                response.setHeader("Vary", "Accept");
                
                String json = objectMapper.writeValueAsString(fullResponse);
                response.getWriter().write(json);
            } else {
                // Initial HTML Bootstrap Load
                response.setContentType(MediaType.TEXT_HTML_VALUE);
                response.setCharacterEncoding("UTF-8");
                
                String html = inertiaRenderer.renderHtmlBootstrap(fullResponse);
                response.getWriter().write(html);
            }
        }
    }
}
