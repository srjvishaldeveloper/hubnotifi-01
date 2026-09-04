package com.whatsmine.config;

import com.whatsmine.realtime.PusherWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PusherWebSocketHandler pusherWebSocketHandler;

    public WebSocketConfig(PusherWebSocketHandler pusherWebSocketHandler) {
        this.pusherWebSocketHandler = pusherWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pusherWebSocketHandler, "/app/{appKey}", "/app/**", "/ws/**")
                .setAllowedOrigins("*");
    }
}
