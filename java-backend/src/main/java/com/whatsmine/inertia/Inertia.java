package com.whatsmine.inertia;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

public class Inertia {

    public static InertiaResponse render(String component) {
        return new InertiaResponse(component, new HashMap<>(), null, null);
    }

    public static InertiaResponse render(String component, Map<String, Object> props) {
        return new InertiaResponse(component, props, null, null);
    }

    public static InertiaRedirect redirect(String url) {
        return new InertiaRedirect(url);
    }

    public static InertiaLocation location(String url) {
        return new InertiaLocation(url);
    }

    public static void flashSuccess(HttpSession session, String message) {
        FlashMessage flash = getOrCreateFlash(session);
        flash.setSuccess(message);
        session.setAttribute(DefaultGlobalPropsProvider.FLASH_SESSION_KEY, flash);
    }

    public static void flashError(HttpSession session, String message) {
        FlashMessage flash = getOrCreateFlash(session);
        flash.setError(message);
        session.setAttribute(DefaultGlobalPropsProvider.FLASH_SESSION_KEY, flash);
    }

    private static FlashMessage getOrCreateFlash(HttpSession session) {
        FlashMessage flash = (FlashMessage) session.getAttribute(DefaultGlobalPropsProvider.FLASH_SESSION_KEY);
        if (flash == null) {
            flash = new FlashMessage();
        }
        return flash;
    }

    public static class InertiaRedirect {
        private final String url;

        public InertiaRedirect(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class InertiaLocation {
        private final String url;

        public InertiaLocation(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }
}
