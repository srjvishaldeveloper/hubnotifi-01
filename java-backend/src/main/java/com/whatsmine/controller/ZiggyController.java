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
                        "broadcasting.auth": { "uri": "broadcasting/auth", "methods": ["POST"] },
                        "billing": { "uri": "billing", "methods": ["GET", "HEAD"] },
                        "profile.edit": { "uri": "profile", "methods": ["GET", "HEAD"] },
                        "pricing": { "uri": "pricing", "methods": ["GET", "HEAD"] },
                        "faq": { "uri": "faq", "methods": ["GET", "HEAD"] },
                        "use-cases": { "uri": "use-cases", "methods": ["GET", "HEAD"] },
                        "integrations": { "uri": "integrations", "methods": ["GET", "HEAD"] },
                        "about": { "uri": "about", "methods": ["GET", "HEAD"] },
                        "contact": { "uri": "contact", "methods": ["GET", "HEAD"] },
                        "contact.store": { "uri": "contact", "methods": ["POST"] },
                        "client.pricing": { "uri": "app/pricing", "methods": ["GET", "HEAD"] },

                        "client.dashboard": { "uri": "dashboard", "methods": ["GET", "HEAD"] },
                        "client.workspaces.index": { "uri": "workspaces", "methods": ["GET", "HEAD"] },
                        "client.team.index": { "uri": "app/team", "methods": ["GET", "HEAD"] },
                        "client.onboarding.show": { "uri": "app/onboarding", "methods": ["GET", "HEAD"] },
                        "client.notifications.index": { "uri": "notifications", "methods": ["GET", "HEAD"] },
                        "client.notifications.recent": { "uri": "notifications/recent", "methods": ["GET", "HEAD"] },
                        "client.profile.edit": { "uri": "profile", "methods": ["GET", "HEAD"] },
                        "client.media.index": { "uri": "media", "methods": ["GET", "HEAD"] },
                        "client.billing.index": { "uri": "billing", "methods": ["GET", "HEAD"] },
                        "client.subscription.show": { "uri": "subscription", "methods": ["GET", "HEAD"] },
                        "client.subscription.invoice": { "uri": "subscription/invoice/{transactionId}", "methods": ["GET", "HEAD"] },
                        "client.support.index": { "uri": "support", "methods": ["GET", "HEAD"] },
                        "client.support.create": { "uri": "support/create", "methods": ["GET", "HEAD"] },
                        "client.support.show": { "uri": "support/{id}", "methods": ["GET", "HEAD"] },
                        "client.webhooks.index": { "uri": "webhooks", "methods": ["GET", "HEAD"] },
                        "client.webhooks.deliveries": { "uri": "webhooks/{id}/deliveries", "methods": ["GET", "HEAD"] },
                        "client.leads.index": { "uri": "app/leads", "methods": ["GET", "HEAD"] },
                        "client.audit-log.index": { "uri": "app/audit-logs", "methods": ["GET", "HEAD"] },
                        "client.automations.index": { "uri": "app/automations", "methods": ["GET", "HEAD"] },
                        "client.automations.edit": { "uri": "app/automations/{uuid}/edit", "methods": ["GET", "HEAD"] },
                        "client.automations.runs": { "uri": "app/automations/{uuid}/runs", "methods": ["GET", "HEAD"] },
                        "client.reports.automations.index": { "uri": "app/reports/automations", "methods": ["GET", "HEAD"] },
                        "client.reports.campaigns.show": { "uri": "app/reports/campaigns/{campaign}", "methods": ["GET", "HEAD"] },
                        "client.reports.inbox.index": { "uri": "app/reports/inbox", "methods": ["GET", "HEAD"] },
                        "client.reports.ai.index": { "uri": "app/reports/ai", "methods": ["GET", "HEAD"] },
                        "client.reports.social.index": { "uri": "app/reports/social", "methods": ["GET", "HEAD"] },
                        "client.ai.chatbots.index": { "uri": "app/ai/chatbots", "methods": ["GET", "HEAD"] },
                        "client.ai.knowledge-bases.index": { "uri": "app/ai/knowledge-bases", "methods": ["GET", "HEAD"] },
                        "client.ai.knowledge-bases.show": { "uri": "app/ai/knowledge-bases/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.ai.providers.index": { "uri": "app/ai/providers", "methods": ["GET", "HEAD"] },
                        "client.inbox.index": { "uri": "app/inbox", "methods": ["GET", "HEAD"] },
                        "client.inbox.show": { "uri": "app/inbox/conversations/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.inbox.contacts.search": { "uri": "app/inbox/contacts/search", "methods": ["GET", "HEAD"] },
                        "client.inbox.channel-accounts": { "uri": "app/inbox/channel-accounts", "methods": ["GET", "HEAD"] },
                        "client.inbox.templates": { "uri": "app/inbox/templates", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.templates.index": { "uri": "app/whatsapp/templates", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.auto-replies.index": { "uri": "app/whatsapp/auto-replies", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.auto-replies.store": { "uri": "app/whatsapp/auto-replies", "methods": ["POST"] },
                        "client.whatsapp.auto-replies.update": { "uri": "app/whatsapp/auto-replies/{id}", "methods": ["PUT"] },
                        "client.whatsapp.auto-replies.destroy": { "uri": "app/whatsapp/auto-replies/{id}", "methods": ["DELETE"] },
                        "client.whatsapp.widget.index": { "uri": "app/whatsapp/widget", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.widgets.create": { "uri": "app/whatsapp/widgets/create", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.widgets.edit": { "uri": "app/whatsapp/widgets/{id}/edit", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.widgets.store": { "uri": "app/whatsapp/widgets", "methods": ["POST"] },
                        "client.whatsapp.widgets.update": { "uri": "app/whatsapp/widgets/{id}", "methods": ["PUT"] },
                        "client.whatsapp.widgets.destroy": { "uri": "app/whatsapp/widgets/{id}", "methods": ["DELETE"] },
                        "client.sms-gateways.index": { "uri": "app/broadcasts/sms-gateways", "methods": ["GET", "HEAD"] },
                        "client.sms-gateways.update": { "uri": "app/broadcasts/sms-gateways/{provider}", "methods": ["PUT"] },
                        "client.sms-gateways.destroy": { "uri": "app/broadcasts/sms-gateways/{provider}", "methods": ["DELETE"] },
                        "client.email-server.index": { "uri": "app/broadcasts/email-server", "methods": ["GET", "HEAD"] },
                        "client.email-server.store": { "uri": "app/broadcasts/email-server", "methods": ["POST"] },
                        "client.email-server.update": { "uri": "app/broadcasts/email-server", "methods": ["PUT"] },
                        "client.email-server.destroy": { "uri": "app/broadcasts/email-server", "methods": ["DELETE"] },
                        "client.email-server.test": { "uri": "app/broadcasts/email-server/test", "methods": ["POST"] },
                        "client.channels.social.index": { "uri": "app/channels/social", "methods": ["GET", "HEAD"] },
                        "client.campaigns.index": { "uri": "app/broadcasts/campaigns", "methods": ["GET", "HEAD"] },
                        "client.campaigns.create": { "uri": "app/broadcasts/campaigns/create", "methods": ["GET", "HEAD"] },
                        "client.campaigns.show": { "uri": "app/broadcasts/campaigns/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.campaigns.edit": { "uri": "app/broadcasts/campaigns/{uuid}/edit", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.orders.index": { "uri": "app/ecommerce/orders", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.orders.show": { "uri": "app/ecommerce/orders/{id}", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.products.index": { "uri": "app/ecommerce/products", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.products.search": { "uri": "app/ecommerce/products/search", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.stores.index": { "uri": "app/ecommerce/stores", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.contacts.orders": { "uri": "app/ecommerce/contacts/{contactId}/orders", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.oauth.connect": { "uri": "app/ecommerce/oauth/{platform}/connect", "methods": ["GET", "HEAD"] },
                        "client.push.subscribe": { "uri": "webpush/subscribe", "methods": ["POST"] },
                        "client.push.unsubscribe": { "uri": "webpush/unsubscribe", "methods": ["POST"] },
                        "client.contacts.index": { "uri": "contacts", "methods": ["GET", "HEAD"] },
                        "client.contacts.show": { "uri": "contacts/{contact}", "methods": ["GET", "HEAD"] },
                        "client.contacts.store": { "uri": "contacts", "methods": ["POST"] },
                        "client.contacts.update": { "uri": "contacts/{contact}", "methods": ["PUT"] },
                        "client.contacts.destroy": { "uri": "contacts/{contact}", "methods": ["DELETE"] },
                        "client.contacts.bulk-destroy": { "uri": "contacts/bulk-destroy", "methods": ["DELETE"] },
                        "client.contacts.export": { "uri": "contacts/export", "methods": ["GET", "HEAD"] },
                        "client.segments.index": { "uri": "segments", "methods": ["GET", "HEAD"] },
                        "client.segments.store": { "uri": "segments", "methods": ["POST"] },
                        "client.segments.update": { "uri": "segments/{segment}", "methods": ["PUT"] },
                        "client.segments.destroy": { "uri": "segments/{segment}", "methods": ["DELETE"] },
                        "client.segments.contacts": { "uri": "segments/{segment}/contacts", "methods": ["GET", "HEAD"] },
                        "client.segments.contacts.attach": { "uri": "segments/{segment}/contacts", "methods": ["POST"] },
                        "client.segments.contacts.detach": { "uri": "segments/{segment}/contacts/{contact}", "methods": ["DELETE"] },

                        "admin.login": { "uri": "admin/login", "methods": ["GET", "HEAD"] },
                        "admin.logout": { "uri": "admin/logout", "methods": ["POST"] },
                        "admin.dashboard": { "uri": "admin/dashboard", "methods": ["GET", "HEAD"] },
                        "admin.clients.index": { "uri": "admin/clients", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.index": { "uri": "admin/subscriptions", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.user-search": { "uri": "admin/subscriptions/user-search", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.export": { "uri": "admin/subscriptions/export", "methods": ["GET", "HEAD"] },
                        "admin.support.index": { "uri": "admin/support", "methods": ["GET", "HEAD"] },
                        "admin.support.show": { "uri": "admin/support/{id}", "methods": ["GET", "HEAD"] },
                        "admin.payments.index": { "uri": "admin/payments", "methods": ["GET", "HEAD"] },
                        "admin.plans.index": { "uri": "admin/plans", "methods": ["GET", "HEAD"] },
                        "admin.coupons.index": { "uri": "admin/coupons", "methods": ["GET", "HEAD"] },
                        "admin.tax-rates.index": { "uri": "admin/tax-rates", "methods": ["GET", "HEAD"] },
                        "admin.payment-gateways.index": { "uri": "admin/payment-gateways", "methods": ["GET", "HEAD"] },
                        "admin.payment-gateways.show": { "uri": "admin/payment-gateways/{gateway}", "methods": ["GET", "HEAD"] },
                        "admin.email-system.index": { "uri": "admin/email-system", "methods": ["GET", "HEAD"] },
                        "admin.locales.index": { "uri": "admin/locales", "methods": ["GET", "HEAD"] },
                        "admin.roles-permissions.index": { "uri": "admin/roles-permissions", "methods": ["GET", "HEAD"] },
                        "admin.admins.index": { "uri": "admin/admins", "methods": ["GET", "HEAD"] },
                        "admin.landing-page.index": { "uri": "admin/landing-page", "methods": ["GET", "HEAD"] },
                        "admin.cms-pages.index": { "uri": "admin/cms-pages", "methods": ["GET", "HEAD"] },
                        "admin.cron-setup.index": { "uri": "admin/cron-setup", "methods": ["GET", "HEAD"] },
                        "admin.settings.index": { "uri": "admin/settings", "methods": ["GET", "HEAD"] },
                        "admin.audit-log.index": { "uri": "admin/audit-logs", "methods": ["GET", "HEAD"] },
                        "admin.impersonation.stop": { "uri": "admin/impersonate/stop", "methods": ["POST"] },
                        "admin.currencies.index": { "uri": "admin/currencies", "methods": ["GET", "HEAD"] },
                        "admin.queue.index": { "uri": "admin/queue", "methods": ["GET", "HEAD"] },
                        "admin.pusher-settings.index": { "uri": "admin/pusher-settings", "methods": ["GET", "HEAD"] },
                        "admin.integrations.index": { "uri": "admin/integrations", "methods": ["GET", "HEAD"] },
                        "admin.integrations.edit": { "uri": "admin/integrations/{provider}", "methods": ["GET", "HEAD"] },
                        "admin.integrations.update": { "uri": "admin/integrations/{provider}", "methods": ["PUT"] },
                        "admin.ai.index": { "uri": "admin/ai", "methods": ["GET", "HEAD"] }

                        // NOTE: the following nav items have NO backend controller at all yet
                        // (not a routing bug — the page was never built in Java): client.settings.*,
                        // client.social.* (composer/posts/calendar/accounts),
                        // client.whatsapp.setup.* (no GET page, actions only), client.inbox.setup,
                        // client.api-tokens.*,
                        // admin.search, admin.integrations.audit-log/edit/update/test/rotate/
                        // set-default (Integrations page itself now loads read-only).
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

