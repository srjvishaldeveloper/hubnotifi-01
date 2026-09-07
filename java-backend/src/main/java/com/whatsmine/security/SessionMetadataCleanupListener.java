package com.whatsmine.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/**
 * Spring Boot auto-registers HttpSessionListener beans with the embedded
 * servlet container, so this needs no extra wiring beyond being a bean.
 */
@Component
public class SessionMetadataCleanupListener implements HttpSessionListener {

    private final SessionMetadataStore sessionMetadataStore;

    public SessionMetadataCleanupListener(SessionMetadataStore sessionMetadataStore) {
        this.sessionMetadataStore = sessionMetadataStore;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        sessionMetadataStore.remove(se.getSession().getId());
    }
}
