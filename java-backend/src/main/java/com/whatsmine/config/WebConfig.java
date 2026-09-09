package com.whatsmine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.InertiaInterceptor;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.inertia.InertiaReturnValueHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final InertiaInterceptor inertiaInterceptor;
    private final InertiaRenderer inertiaRenderer;
    private final ObjectMapper objectMapper;

    public WebConfig(InertiaInterceptor inertiaInterceptor, InertiaRenderer inertiaRenderer, ObjectMapper objectMapper) {
        this.inertiaInterceptor = inertiaInterceptor;
        this.inertiaRenderer = inertiaRenderer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(inertiaInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/build/**", "/assets/**", "/storage/**", "/i18n/**", "/images/**", "/favicon.ico", "/whatsmine-logo.png", "/*.png", "/*.svg", "/*.ico", "/*.js");
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new InertiaReturnValueHandler(inertiaRenderer, objectMapper));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static build assets (compiled Vite JS/CSS)
        registry.addResourceHandler("/build/**")
                .addResourceLocations("classpath:/static/build/", "file:src/main/resources/static/build/", "file:../php/public/build/", "file:public/build/");

        // Serve assets under /assets/**
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/", "file:src/main/resources/static/assets/", "file:../php/public/assets/", "file:public/assets/");

        // Serve static public assets under /images/** (provider brand logos, etc.) —
        // location must include the "images/" segment since the "**" match only
        // captures the path AFTER "/images/", e.g. "/images/integrations/meta.svg"
        // resolves to "integrations/meta.svg" under this location.
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/", "file:src/main/resources/static/images/", "file:../php/public/images/", "file:public/images/");

        // Serve root-level static assets (logo, favicon, etc.)
        registry.addResourceHandler("/whatsmine-icon.svg", "/whatsmine-logo.png", "/favicon.ico", "/*.png", "/*.svg", "/*.ico", "/*.js")
                .addResourceLocations("classpath:/static/", "file:src/main/resources/static/", "file:../php/public/", "file:public/");

        // Serve storage uploads
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("classpath:/static/storage/", "file:storage/app/public/", "file:../php/storage/app/public/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
