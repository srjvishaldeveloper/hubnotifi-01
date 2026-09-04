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
                .excludePathPatterns("/build/**", "/storage/**", "/favicon.ico");
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

        // Serve static public assets (images, icons, etc.)
        registry.addResourceHandler("/images/**", "/whatsmine-icon.svg", "/favicon.ico", "/*.png", "/*.svg")
                .addResourceLocations("classpath:/static/", "file:../php/public/", "file:public/");

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
