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

    @GetMapping(value = "/app-routes.js", produces = "application/javascript")
    public ResponseEntity<String> appRoutesJs() {
        String js = """
            (function () {
                const AppRoutes = {
                    url: (typeof window !== 'undefined' ? window.location.origin : "http://localhost:8080"),
                    port: (typeof window !== 'undefined' && window.location.port ? parseInt(window.location.port) : 8080),
                    defaults: {},
                    routes: {
                        "welcome": { "uri": "", "methods": ["GET", "HEAD"] },
                        "home": { "uri": "", "methods": ["GET", "HEAD"] },
                        "login": { "uri": "login", "methods": ["GET", "HEAD"] },
                        "register": { "uri": "register", "methods": ["GET", "HEAD"] },
                        "password.request": { "uri": "forgot-password", "methods": ["GET", "HEAD"] },
                        "password.reset": { "uri": "reset-password/{token}", "methods": ["GET", "HEAD"] },
                        "logout": { "uri": "logout", "methods": ["POST"] },
                        "auth.two-factor.verify": { "uri": "two-factor-challenge", "methods": ["POST"] },
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
                        "client.workspaces.store": { "uri": "workspaces", "methods": ["POST"] },
                        "client.workspaces.switch": { "uri": "workspaces/switch", "methods": ["POST"] },
                        "client.profile.2fa": { "uri": "profile/two-factor", "methods": ["GET", "HEAD"] },
                        "client.profile.2fa.enable": { "uri": "profile/two-factor/enable", "methods": ["POST"] },
                        "client.profile.2fa.disable": { "uri": "profile/two-factor/disable", "methods": ["DELETE"] },
                        "client.profile.2fa.recovery-codes": { "uri": "profile/two-factor/recovery-codes", "methods": ["POST"] },
                        "client.profile.sessions": { "uri": "profile/sessions", "methods": ["GET", "HEAD"] },
                        "client.profile.sessions.destroy": { "uri": "profile/sessions", "methods": ["DELETE"] },
                        "client.team.index": { "uri": "app/team", "methods": ["GET", "HEAD"] },
                        "client.team.store": { "uri": "app/team/members", "methods": ["POST"] },
                        "client.team.destroy": { "uri": "app/team/members/{userId}", "methods": ["DELETE"] },
                        "client.onboarding.show": { "uri": "app/onboarding", "methods": ["GET", "HEAD"] },
                        "client.notifications.index": { "uri": "notifications", "methods": ["GET", "HEAD"] },
                        "client.notifications.recent": { "uri": "notifications/recent", "methods": ["GET", "HEAD"] },
                        "client.notifications.destroy": { "uri": "notifications/{id}", "methods": ["DELETE"] },
                        "client.notifications.read": { "uri": "notifications/{id}/read", "methods": ["POST"] },
                        "client.notifications.read-all": { "uri": "notifications/mark-all-read", "methods": ["POST"] },
                        "client.notification-preferences.update": { "uri": "notifications/preferences", "methods": ["POST"] },
                        "client.profile.edit": { "uri": "profile", "methods": ["GET", "HEAD"] },
                        "client.profile.update": { "uri": "profile", "methods": ["PATCH"] },
                        "client.media.index": { "uri": "media", "methods": ["GET", "HEAD"] },
                        "client.media.store": { "uri": "media", "methods": ["POST"] },
                        "client.media.destroy": { "uri": "media/{id}", "methods": ["DELETE"] },
                        "client.billing.index": { "uri": "billing", "methods": ["GET", "HEAD"] },
                        "client.subscription.show": { "uri": "subscription", "methods": ["GET", "HEAD"] },
                        "client.subscription.invoice": { "uri": "subscription/invoice/{transactionId}", "methods": ["GET", "HEAD"] },
                        "client.subscription.change-plan": { "uri": "subscription/change-plan", "methods": ["POST"] },
                        "client.subscription.destroy": { "uri": "subscription", "methods": ["DELETE"] },
                        "client.checkout.store": { "uri": "checkout", "methods": ["POST"] },
                        "client.coupon.check": { "uri": "coupon/check", "methods": ["POST"] },
                        "client.support.index": { "uri": "support", "methods": ["GET", "HEAD"] },
                        "client.support.create": { "uri": "support/create", "methods": ["GET", "HEAD"] },
                        "client.support.show": { "uri": "support/{id}", "methods": ["GET", "HEAD"] },
                        "client.support.store": { "uri": "support", "methods": ["POST"] },
                        "client.support.reply": { "uri": "support/{id}/reply", "methods": ["POST"] },
                        "client.webhooks.index": { "uri": "webhooks", "methods": ["GET", "HEAD"] },
                        "client.webhooks.deliveries": { "uri": "webhooks/{id}/deliveries", "methods": ["GET", "HEAD"] },
                        "client.webhooks.store": { "uri": "webhooks", "methods": ["POST"] },
                        "client.webhooks.update": { "uri": "webhooks/{id}", "methods": ["PUT"] },
                        "client.webhooks.destroy": { "uri": "webhooks/{id}", "methods": ["DELETE"] },
                        "client.webhooks.rotate-secret": { "uri": "webhooks/{id}/rotate-secret", "methods": ["POST"] },
                        "client.webhooks.test": { "uri": "webhooks/{id}/test", "methods": ["POST"] },
                        "client.leads.index": { "uri": "app/leads", "methods": ["GET", "HEAD"] },
                        "client.leads.scrape": { "uri": "app/leads/scrape", "methods": ["POST"] },
                        "client.leads.push-to-contacts": { "uri": "app/leads/push-to-contacts", "methods": ["POST"] },
                        "client.leads.destroy": { "uri": "app/leads/{id}", "methods": ["DELETE"] },
                        "client.audit-log.index": { "uri": "app/audit-logs", "methods": ["GET", "HEAD"] },
                        "client.automations.index": { "uri": "app/automations", "methods": ["GET", "HEAD"] },
                        "client.automations.store": { "uri": "app/automations", "methods": ["POST"] },
                        "client.automations.generate": { "uri": "app/automations/generate", "methods": ["POST"] },
                        "client.automations.edit": { "uri": "app/automations/{uuid}/edit", "methods": ["GET", "HEAD"] },
                        "client.automations.update": { "uri": "app/automations/{uuid}", "methods": ["PUT"] },
                        "client.automations.destroy": { "uri": "app/automations/{uuid}", "methods": ["DELETE"] },
                        "client.automations.runs": { "uri": "app/automations/{uuid}/runs", "methods": ["GET", "HEAD"] },
                        "client.automations.test": { "uri": "app/automations/{uuid}/test", "methods": ["POST"] },
                        "client.automations.generate-token": { "uri": "app/automations/{uuid}/token", "methods": ["POST"] },
                        "client.reports.automations.index": { "uri": "app/reports/automations", "methods": ["GET", "HEAD"] },
                        "client.reports.campaigns.index": { "uri": "app/reports/campaigns", "methods": ["GET", "HEAD"] },
                        "client.reports.campaigns.show": { "uri": "app/reports/campaigns/{campaign}", "methods": ["GET", "HEAD"] },
                        "client.reports.inbox.index": { "uri": "app/reports/inbox", "methods": ["GET", "HEAD"] },
                        "client.reports.ai.index": { "uri": "app/reports/ai", "methods": ["GET", "HEAD"] },
                        "client.reports.social.index": { "uri": "app/reports/social", "methods": ["GET", "HEAD"] },
                        "client.ai.chatbots.index": { "uri": "app/ai/chatbots", "methods": ["GET", "HEAD"] },
                        "client.ai.chatbots.store": { "uri": "app/ai/chatbots", "methods": ["POST"] },
                        "client.ai.chatbots.update": { "uri": "app/ai/chatbots/{uuid}", "methods": ["PUT"] },
                        "client.ai.chatbots.destroy": { "uri": "app/ai/chatbots/{uuid}", "methods": ["DELETE"] },
                        "client.ai.chatbots.playground": { "uri": "app/ai/chatbots/{uuid}/playground", "methods": ["POST"] },
                        "client.ai.knowledge-bases.index": { "uri": "app/ai/knowledge-bases", "methods": ["GET", "HEAD"] },
                        "client.ai.knowledge-bases.show": { "uri": "app/ai/knowledge-bases/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.ai.knowledge-bases.store": { "uri": "app/ai/knowledge-bases", "methods": ["POST"] },
                        "client.ai.knowledge-bases.documents.add": { "uri": "app/ai/knowledge-bases/{uuid}/documents", "methods": ["POST"] },
                        "client.ai.documents.reindex": { "uri": "app/ai/documents/{uuid}/reindex", "methods": ["POST"] },
                        "client.ai.documents.destroy": { "uri": "app/ai/documents/{uuid}", "methods": ["DELETE"] },
                        "client.ai.providers.index": { "uri": "app/ai/providers", "methods": ["GET", "HEAD"] },
                        "client.ai.providers.update": { "uri": "app/ai/providers/{provider}", "methods": ["PUT"] },
                        "client.inbox.index": { "uri": "app/inbox", "methods": ["GET", "HEAD"] },
                        "client.inbox.show": { "uri": "app/inbox/conversations/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.inbox.contacts.search": { "uri": "app/inbox/contacts/search", "methods": ["GET", "HEAD"] },
                        "client.inbox.channel-accounts": { "uri": "app/inbox/channel-accounts", "methods": ["GET", "HEAD"] },
                        "client.inbox.templates": { "uri": "app/inbox/templates", "methods": ["GET", "HEAD"] },
                        "client.inbox.upload-media": { "uri": "app/inbox/conversations/{conversation}/upload-media", "methods": ["POST"] },
                        "client.inbox.message-media": { "uri": "app/inbox/conversations/{conversation}/messages/{message}/media", "methods": ["GET", "HEAD"] },
                        "client.inbox.start": { "uri": "app/inbox/conversations/start", "methods": ["POST"] },
                        "client.inbox.reply": { "uri": "app/inbox/conversations/{conversation}/reply", "methods": ["POST"] },
                        "client.inbox.share-product": { "uri": "app/inbox/conversations/{conversation}/share-product", "methods": ["POST"] },
                        "client.inbox.assign": { "uri": "app/inbox/conversations/{conversation}/assign", "methods": ["POST"] },
                        "client.inbox.status": { "uri": "app/inbox/conversations/{conversation}/status", "methods": ["POST"] },
                        "client.inbox.typing": { "uri": "app/inbox/conversations/{conversation}/typing", "methods": ["POST"] },
                        "client.inbox.handover": { "uri": "app/inbox/conversations/{conversation}/handover", "methods": ["POST"] },
                        "client.inbox.notes.index": { "uri": "app/inbox/conversations/{conversation}/notes", "methods": ["GET", "HEAD"] },
                        "client.inbox.notes.store": { "uri": "app/inbox/conversations/{conversation}/notes", "methods": ["POST"] },
                        "client.inbox.canned-replies.index": { "uri": "app/inbox/canned-replies", "methods": ["GET", "HEAD"] },
                        "client.inbox.canned-replies.list": { "uri": "app/inbox/canned-replies/list", "methods": ["GET", "HEAD"] },
                        "client.inbox.canned-replies.store": { "uri": "app/inbox/canned-replies", "methods": ["POST"] },
                        "client.inbox.canned-replies.update": { "uri": "app/inbox/canned-replies/{cannedReply}", "methods": ["PUT"] },
                        "client.inbox.canned-replies.destroy": { "uri": "app/inbox/canned-replies/{cannedReply}", "methods": ["DELETE"] },
                        "client.inbox.labels.index": { "uri": "app/inbox/labels", "methods": ["GET", "HEAD"] },
                        "client.inbox.labels.store": { "uri": "app/inbox/labels", "methods": ["POST"] },
                        "client.inbox.labels.update": { "uri": "app/inbox/labels/{label}", "methods": ["PUT"] },
                        "client.inbox.labels.destroy": { "uri": "app/inbox/labels/{label}", "methods": ["DELETE"] },
                        "client.inbox.labels.attach": { "uri": "app/inbox/conversations/{conversation}/labels", "methods": ["POST"] },
                        "client.inbox.labels.detach": { "uri": "app/inbox/conversations/{conversation}/labels/{label}", "methods": ["DELETE"] },
                        "client.whatsapp.templates.index": { "uri": "app/whatsapp/templates", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.templates.create": { "uri": "app/whatsapp/templates/create", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.templates.store": { "uri": "app/whatsapp/templates", "methods": ["POST"] },
                        "client.whatsapp.templates.sync": { "uri": "app/whatsapp/templates/sync", "methods": ["POST"] },
                        "client.whatsapp.templates.upload-media": { "uri": "app/whatsapp/templates/upload-media", "methods": ["POST"] },
                        "client.whatsapp.templates.edit": { "uri": "app/whatsapp/templates/{template}/edit", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.templates.update": { "uri": "app/whatsapp/templates/{template}", "methods": ["PUT"] },
                        "client.whatsapp.templates.destroy": { "uri": "app/whatsapp/templates/{template}", "methods": ["DELETE"] },
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
                        "client.sms-gateways.test": { "uri": "app/broadcasts/sms-gateways/test", "methods": ["POST"] },
                        "client.email-server.index": { "uri": "app/broadcasts/email-server", "methods": ["GET", "HEAD"] },
                        "client.email-server.store": { "uri": "app/broadcasts/email-server", "methods": ["POST"] },
                        "client.email-server.update": { "uri": "app/broadcasts/email-server", "methods": ["PUT"] },
                        "client.email-server.destroy": { "uri": "app/broadcasts/email-server", "methods": ["DELETE"] },
                        "client.email-server.test": { "uri": "app/broadcasts/email-server/test", "methods": ["POST"] },
                        "client.channels.social.index": { "uri": "app/channels/social", "methods": ["GET", "HEAD"] },
                        "client.campaigns.index": { "uri": "app/broadcasts/campaigns", "methods": ["GET", "HEAD"] },
                        "client.campaigns.create": { "uri": "app/broadcasts/campaigns/create", "methods": ["GET", "HEAD"] },
                        "client.campaigns.store": { "uri": "app/broadcasts/campaigns", "methods": ["POST"] },
                        "client.campaigns.store-draft": { "uri": "app/broadcasts/campaigns/draft", "methods": ["POST"] },
                        "client.campaigns.audience-preview": { "uri": "app/broadcasts/campaigns/audience-preview", "methods": ["POST"] },
                        "client.campaigns.show": { "uri": "app/broadcasts/campaigns/{uuid}", "methods": ["GET", "HEAD"] },
                        "client.campaigns.edit": { "uri": "app/broadcasts/campaigns/{uuid}/edit", "methods": ["GET", "HEAD"] },
                        "client.campaigns.update": { "uri": "app/broadcasts/campaigns/{uuid}", "methods": ["PATCH"] },
                        "client.campaigns.test-send": { "uri": "app/broadcasts/campaigns/{uuid}/test-send", "methods": ["POST"] },
                        "client.campaigns.launch": { "uri": "app/broadcasts/campaigns/{uuid}/launch", "methods": ["POST"] },
                        "client.campaigns.pause": { "uri": "app/broadcasts/campaigns/{uuid}/pause", "methods": ["POST"] },
                        "client.campaigns.destroy": { "uri": "app/broadcasts/campaigns/{uuid}", "methods": ["DELETE"] },
                        "client.campaigns.generate-email": { "uri": "app/broadcasts/campaigns/generate-email", "methods": ["POST"] },
                        "client.campaigns.improve-subject": { "uri": "app/broadcasts/campaigns/improve-subject", "methods": ["POST"] },
                        "client.ecommerce.orders.index": { "uri": "app/ecommerce/orders", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.orders.show": { "uri": "app/ecommerce/orders/{id}", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.orders.refresh": { "uri": "app/ecommerce/orders/{id}/refresh", "methods": ["POST"] },
                        "client.ecommerce.orders.fulfill": { "uri": "app/ecommerce/orders/{id}/fulfill", "methods": ["POST"] },
                        "client.ecommerce.products.index": { "uri": "app/ecommerce/products", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.products.search": { "uri": "app/ecommerce/products/search", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.stores.index": { "uri": "app/ecommerce/stores", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.stores.store": { "uri": "app/ecommerce/stores", "methods": ["POST"] },
                        "client.ecommerce.stores.destroy": { "uri": "app/ecommerce/stores/{id}", "methods": ["DELETE"] },
                        "client.ecommerce.contacts.orders": { "uri": "app/ecommerce/contacts/{contactId}/orders", "methods": ["GET", "HEAD"] },
                        "client.ecommerce.oauth.connect": { "uri": "app/ecommerce/oauth/{platform}/connect", "methods": ["GET", "HEAD"] },
                        "client.push.subscribe": { "uri": "webpush/subscribe", "methods": ["POST"] },
                        "client.push.unsubscribe": { "uri": "webpush/unsubscribe", "methods": ["POST"] },
                        "reports.exports.contacts": { "uri": "reports/exports/contacts", "methods": ["GET", "HEAD"] },
                        "reports.exports.campaign-recipients": { "uri": "reports/exports/campaign-recipients/{uuid}", "methods": ["GET", "HEAD"] },
                        "reports.exports.conversations": { "uri": "reports/exports/conversations", "methods": ["GET", "HEAD"] },
                        "reports.exports.ai-runs": { "uri": "reports/exports/ai-runs", "methods": ["GET", "HEAD"] },
                        "reports.exports.audit-log": { "uri": "reports/exports/audit-log", "methods": ["GET", "HEAD"] },
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
                        "admin.clients.store": { "uri": "admin/clients", "methods": ["POST"] },
                        "admin.clients.update": { "uri": "admin/clients/{client}", "methods": ["PUT"] },
                        "admin.clients.destroy": { "uri": "admin/clients/{client}", "methods": ["DELETE"] },
                        "admin.clients.export": { "uri": "admin/clients/export", "methods": ["GET", "HEAD"] },
                        "admin.clients.users.index": { "uri": "admin/clients/{client}/users", "methods": ["GET", "HEAD"] },
                        "admin.clients.users.store": { "uri": "admin/clients/{client}/users", "methods": ["POST"] },
                        "admin.clients.users.update": { "uri": "admin/clients/{client}/users/{user}", "methods": ["PUT"] },
                        "admin.clients.users.destroy": { "uri": "admin/clients/{client}/users/{user}", "methods": ["DELETE"] },
                        "admin.clients.assign-plan": { "uri": "admin/clients/{client}/assign-plan", "methods": ["POST"] },
                        "admin.clients.impersonate": { "uri": "admin/clients/{client}/impersonate", "methods": ["POST"] },
                        "admin.subscriptions.index": { "uri": "admin/subscriptions", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.user-search": { "uri": "admin/subscriptions/user-search", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.export": { "uri": "admin/subscriptions/export", "methods": ["GET", "HEAD"] },
                        "admin.subscriptions.store": { "uri": "admin/subscriptions", "methods": ["POST"] },
                        "admin.support.index": { "uri": "admin/support", "methods": ["GET", "HEAD"] },
                        "admin.support.show": { "uri": "admin/support/{id}", "methods": ["GET", "HEAD"] },
                        "admin.support.reply": { "uri": "admin/support/{id}/reply", "methods": ["POST"] },
                        "admin.support.status": { "uri": "admin/support/{id}/status", "methods": ["POST"] },
                        "admin.payments.index": { "uri": "admin/payments", "methods": ["GET", "HEAD"] },
                        "admin.plans.index": { "uri": "admin/plans", "methods": ["GET", "HEAD"] },
                        "admin.plans.store": { "uri": "admin/plans", "methods": ["POST"] },
                        "admin.plans.update": { "uri": "admin/plans/{id}", "methods": ["PUT"] },
                        "admin.plans.destroy": { "uri": "admin/plans/{id}", "methods": ["DELETE"] },
                        "admin.plans.duplicate": { "uri": "admin/plans/{id}/duplicate", "methods": ["POST"] },
                        "admin.plans.reorder": { "uri": "admin/plans/reorder", "methods": ["POST"] },
                        "admin.coupons.index": { "uri": "admin/coupons", "methods": ["GET", "HEAD"] },
                        "admin.coupons.store": { "uri": "admin/coupons", "methods": ["POST"] },
                        "admin.coupons.update": { "uri": "admin/coupons/{id}", "methods": ["PUT"] },
                        "admin.coupons.destroy": { "uri": "admin/coupons/{id}", "methods": ["DELETE"] },
                        "admin.tax-rates.index": { "uri": "admin/tax-rates", "methods": ["GET", "HEAD"] },
                        "admin.tax-rates.store": { "uri": "admin/tax-rates", "methods": ["POST"] },
                        "admin.tax-rates.update": { "uri": "admin/tax-rates/{id}", "methods": ["PUT"] },
                        "admin.tax-rates.destroy": { "uri": "admin/tax-rates/{id}", "methods": ["DELETE"] },
                        "admin.payment-gateways.index": { "uri": "admin/payment-gateways", "methods": ["GET", "HEAD"] },
                        "admin.payment-gateways.show": { "uri": "admin/payment-gateways/{gateway}", "methods": ["GET", "HEAD"] },
                        "admin.payment-gateways.update": { "uri": "admin/payment-gateways/{gateway}", "methods": ["PUT"] },
                        "admin.email-system.index": { "uri": "admin/email-system", "methods": ["GET", "HEAD"] },
                        "admin.locales.index": { "uri": "admin/locales", "methods": ["GET", "HEAD"] },
                        "admin.locales.store": { "uri": "admin/locales", "methods": ["POST"] },
                        "admin.translations.update": { "uri": "admin/translations", "methods": ["PUT"] },
                        "admin.roles-permissions.index": { "uri": "admin/roles-permissions", "methods": ["GET", "HEAD"] },
                        "admin.roles.store": { "uri": "admin/roles", "methods": ["POST"] },
                        "admin.roles.update": { "uri": "admin/roles/{id}", "methods": ["PUT"] },
                        "admin.roles.destroy": { "uri": "admin/roles/{id}", "methods": ["DELETE"] },
                        "admin.admins.index": { "uri": "admin/admins", "methods": ["GET", "HEAD"] },
                        "admin.admins.store": { "uri": "admin/admins", "methods": ["POST"] },
                        "admin.admins.update": { "uri": "admin/admins/{id}", "methods": ["PUT"] },
                        "admin.admins.destroy": { "uri": "admin/admins/{id}", "methods": ["DELETE"] },
                        "admin.admins.toggle-status": { "uri": "admin/admins/{id}/toggle-status", "methods": ["POST"] },
                        "admin.landing-page.index": { "uri": "admin/landing-page", "methods": ["GET", "HEAD"] },
                        "admin.landing-page.update": { "uri": "admin/landing-page", "methods": ["PUT"] },
                        "admin.cms-pages.index": { "uri": "admin/cms-pages", "methods": ["GET", "HEAD"] },
                        "admin.cms-pages.store": { "uri": "admin/cms-pages", "methods": ["POST"] },
                        "admin.cms-pages.update": { "uri": "admin/cms-pages/{id}", "methods": ["PUT"] },
                        "admin.cms-pages.destroy": { "uri": "admin/cms-pages/{id}", "methods": ["DELETE"] },
                        "admin.cron-setup.index": { "uri": "admin/cron-setup", "methods": ["GET", "HEAD"] },
                        "admin.settings.index": { "uri": "admin/settings", "methods": ["GET", "HEAD"] },
                        "admin.settings.update": { "uri": "admin/settings", "methods": ["PUT"] },
                        "admin.audit-log.index": { "uri": "admin/audit-logs", "methods": ["GET", "HEAD"] },
                        "admin.impersonation.stop": { "uri": "admin/impersonation/stop", "methods": ["POST"] },
                        "admin.currencies.index": { "uri": "admin/currencies", "methods": ["GET", "HEAD"] },
                        "admin.currencies.store": { "uri": "admin/currencies", "methods": ["POST"] },
                        "admin.currencies.update": { "uri": "admin/currencies/{code}", "methods": ["PUT"] },
                        "admin.queue.index": { "uri": "admin/queue", "methods": ["GET", "HEAD"] },
                        "admin.queue.retry": { "uri": "admin/queue/{id}/retry", "methods": ["POST"] },
                        "admin.queue.delete-failed": { "uri": "admin/queue/{id}", "methods": ["DELETE"] },
                        "admin.queue.retry-all": { "uri": "admin/queue/retry-all", "methods": ["POST"] },
                        "admin.queue.flush": { "uri": "admin/queue/flush", "methods": ["POST"] },
                        "admin.pusher-settings.index": { "uri": "admin/pusher-settings", "methods": ["GET", "HEAD"] },
                        "admin.pusher-settings.update": { "uri": "admin/pusher-settings", "methods": ["PUT"] },
                        "admin.pusher-settings.test": { "uri": "admin/pusher-settings/test", "methods": ["POST"] },
                        "admin.integrations.index": { "uri": "admin/integrations", "methods": ["GET", "HEAD"] },
                        "admin.integrations.edit": { "uri": "admin/integrations/{provider}", "methods": ["GET", "HEAD"] },
                        "admin.integrations.update": { "uri": "admin/integrations/{provider}", "methods": ["PUT"] },
                        "admin.integrations.test": { "uri": "admin/integrations/{provider}/test", "methods": ["POST"] },
                        "admin.integrations.rotate": { "uri": "admin/integrations/{provider}/rotate", "methods": ["POST"] },
                        "admin.ai.index": { "uri": "admin/ai", "methods": ["GET", "HEAD"] },
                        "admin.license.index": { "uri": "admin/license", "methods": ["GET", "HEAD"] },
                        "admin.license.check-update": { "uri": "admin/license/check-update", "methods": ["POST"] },
                        "admin.license.apply-update": { "uri": "admin/license/apply-update", "methods": ["POST"] },
                        "admin.license.activate": { "uri": "admin/license/activate", "methods": ["POST"] },
                        "admin.license.deactivate": { "uri": "admin/license/deactivate", "methods": ["POST"] },
                        "license.show": { "uri": "license", "methods": ["GET", "HEAD"] },
                        "license.activate": { "uri": "license/activate", "methods": ["POST"] },
                        "client.social.accounts.index": { "uri": "app/social/accounts", "methods": ["GET", "HEAD"] },
                        "client.social.accounts.connect": { "uri": "app/social/accounts/connect/{network}", "methods": ["GET", "HEAD"] },
                        "client.social.oauth.callback": { "uri": "app/social/accounts/callback/{network}", "methods": ["GET", "HEAD"] },
                        "client.social.accounts.disconnect": { "uri": "app/social/accounts/{account}", "methods": ["DELETE"] },
                        "client.social.posts.index": { "uri": "app/social/posts", "methods": ["GET", "HEAD"] },
                        "client.social.composer": { "uri": "app/social/composer", "methods": ["GET", "HEAD"] },
                        "client.social.posts.store": { "uri": "app/social/posts", "methods": ["POST"] },
                        "client.social.posts.edit": { "uri": "app/social/posts/{post}/edit", "methods": ["GET", "HEAD"] },
                        "client.social.posts.update": { "uri": "app/social/posts/{post}", "methods": ["PUT"] },
                        "client.social.posts.destroy": { "uri": "app/social/posts/{post}", "methods": ["DELETE"] },
                        "client.social.posts.publish-now": { "uri": "app/social/posts/{post}/publish-now", "methods": ["POST"] },
                        "client.social.posts.cancel": { "uri": "app/social/posts/{post}/cancel", "methods": ["POST"] },
                        "client.social.ai-generate": { "uri": "app/social/ai-generate", "methods": ["POST"] },
                        "client.social.ai-plan": { "uri": "app/social/ai-plan", "methods": ["POST"] },
                        "client.social.posts.bulk": { "uri": "app/social/posts/bulk", "methods": ["POST"] },
                        "client.social.calendar": { "uri": "app/social/calendar", "methods": ["GET", "HEAD"] },
                        "client.whatsapp.setup.embedded-signup": { "uri": "app/whatsapp/setup/embedded-signup", "methods": ["POST"] },
                        "client.whatsapp.setup.reregister-webhook": { "uri": "app/whatsapp/setup/{waba}/reregister-webhook", "methods": ["POST"] },
                        "client.whatsapp.setup.destroy": { "uri": "app/whatsapp/setup/{waba}", "methods": ["DELETE"] },
                        "client.whatsapp.setup.sync-phone-numbers": { "uri": "app/whatsapp/setup/{waba}/sync-phone-numbers", "methods": ["POST"] },
                        "client.whatsapp.setup.refresh-phone-status": { "uri": "app/whatsapp/setup/{waba}/phone/{phoneNumberId}/refresh-status", "methods": ["POST"] },
                        "client.whatsapp.setup.change-display-name": { "uri": "app/whatsapp/setup/{waba}/phone/{phoneNumberId}/change-name", "methods": ["POST"] },
                        "client.inbox.setup": { "uri": "app/inbox/setup", "methods": ["GET", "HEAD"] },
                        "client.inbox.setup.embedded-signup.instagram": { "uri": "app/inbox/setup/embedded-signup/instagram", "methods": ["POST"] },
                        "client.inbox.setup.embedded-signup.messenger": { "uri": "app/inbox/setup/embedded-signup/messenger", "methods": ["POST"] },
                        "client.inbox.setup.assign-chatbot": { "uri": "app/inbox/setup/{channelAccount}/chatbot", "methods": ["PATCH"] },
                        "client.inbox.setup.destroy": { "uri": "app/inbox/setup/{channelAccount}", "methods": ["DELETE"] },
                        "client.whatsapp.setup": { "uri": "app/inbox/setup", "methods": ["GET", "HEAD"] }

                        // NOTE: the following nav items have NO backend controller at all yet
                        // (not a routing bug — the page was never built in Java): client.settings.*,
                        // client.api-tokens.*,
                        // admin.search, admin.integrations.audit-log/set-default
                        // (Integrations page itself now loads read-only for those).
                    }
                };

                if (typeof window !== 'undefined' && window.AppRoutes && window.AppRoutes.routes) {
                    Object.assign(AppRoutes.routes, window.AppRoutes.routes);
                }
                if (typeof window !== 'undefined') {
                    window.AppRoutes = AppRoutes;
                }

                // Converts a route's {param} URI template into a matcher regex — a literal
                // path === uri comparison (the old behavior) never matches routes that take
                // parameters, e.g. "contacts/{contact}" vs "/contacts/42".
                function routeUriToRegex(uri) {
                    var normalized = uri.replace(/^\\//, '');
                    var segments = normalized.split('/').map(function (seg) {
                        if (/^\\{.+\\}$/.test(seg)) return '[^/]+';
                        return seg.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');
                    }).join('/');
                    return new RegExp('^' + segments + '$');
                }

                // route().current(pattern) must support wildcards ("client.social.accounts.*"),
                // which is how the sidebar (useClientNav.jsx) decides what to highlight. The previous
                // implementation only did an exact AppRoutes.routes[pattern] lookup, which is always
                // undefined for a wildcard string — so every nav item whose activePattern contained "*"
                // (i.e. every multi-route section) never highlighted, even while genuinely on one of its pages.
                function routeCurrentMatches(pattern, registry) {
                    if (typeof window === 'undefined') return false;
                    var path = window.location.pathname.replace(/^\\//, '');
                    if (!pattern) return path;
                    if (pattern.indexOf('*') === -1) {
                        var r = registry.routes[pattern];
                        if (!r) return false;
                        return routeUriToRegex(r.uri).test(path);
                    }
                    var nameRegex = new RegExp('^' + pattern.split('*').map(function (part) {
                        return part.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');
                    }).join('.*') + '$');
                    for (var routeName in registry.routes) {
                        if (nameRegex.test(routeName) && routeUriToRegex(registry.routes[routeName].uri).test(path)) {
                            return true;
                        }
                    }
                    return false;
                }

                function route(name, params, absolute = false, customRegistry = AppRoutes) {
                    if (!name) {
                        return {
                            current: (currentName) => routeCurrentMatches(currentName, customRegistry)
                        };
                    }

                    let routeObj = customRegistry.routes[name];
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

                    return absolute ? (customRegistry.url + uri) : uri;
                }

                route.current = (name) => routeCurrentMatches(name, AppRoutes);

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

