package com.whatsmine.inertia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class InertiaConfig implements BeanPostProcessor {

    private final InertiaRenderer inertiaRenderer;
    private final ObjectMapper objectMapper;

    public InertiaConfig(InertiaRenderer inertiaRenderer, ObjectMapper objectMapper) {
        this.inertiaRenderer = inertiaRenderer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RequestMappingHandlerAdapter adapter) {
            List<HandlerMethodReturnValueHandler> currentHandlers = adapter.getReturnValueHandlers();
            if (currentHandlers != null) {
                List<HandlerMethodReturnValueHandler> newHandlers = new ArrayList<>();
                // Place InertiaReturnValueHandler at index 0 so it takes precedence over @ResponseBody
                newHandlers.add(new InertiaReturnValueHandler(inertiaRenderer, objectMapper));
                newHandlers.addAll(currentHandlers);
                adapter.setReturnValueHandlers(newHandlers);
            }
        }
        return bean;
    }
}
