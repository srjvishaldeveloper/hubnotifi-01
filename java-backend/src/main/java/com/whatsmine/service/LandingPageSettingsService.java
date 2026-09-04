package com.whatsmine.service;

import com.whatsmine.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of PHP's Admin\LandingPageController::defaults()/getPublicSettings().
 * The public Welcome page and every marketing sub-page render entirely off
 * this key set (each section checks `landing.<key>_enabled === '1'`), so a
 * missing default here is a missing section on the live site — keep this in
 * sync with the PHP defaults() array if either one changes.
 */
@Service
public class LandingPageSettingsService {

    private final SystemSettingRepository systemSettingRepository;

    public LandingPageSettingsService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();

        d.put("landing.page_enabled", "1");

        d.put("landing.signin_label", "Sign In");
        d.put("landing.signin_link_type", "dynamic");
        d.put("landing.signin_link_url", "");
        d.put("landing.getstarted_label", "Start Free Trial");
        d.put("landing.getstarted_link_type", "dynamic");
        d.put("landing.getstarted_link_url", "");

        d.put("landing.seo_title", "Hub Notification — One Inbox for WhatsApp, Messenger & Instagram");
        d.put("landing.seo_description", "Hub Notification unifies WhatsApp, Messenger and Instagram in one inbox with AI chatbots, no-code automation, bulk broadcasting and a built-in CRM. Start free — no credit card required.");
        d.put("landing.seo_keywords", "WhatsApp Business API, team inbox, WhatsApp marketing, AI chatbot, Messenger, Instagram DM, bulk messaging, marketing automation, CRM");
        d.put("landing.seo_og_image", "");

        d.put("landing.hero_enabled", "1");
        d.put("landing.hero_badge", "Now with AI chatbots & multi-channel automation");
        d.put("landing.hero_title", "Every Customer Conversation, One Smart Inbox");
        d.put("landing.hero_subtitle", "Unify WhatsApp, Messenger and Instagram, automate replies with AI chatbots, run bulk broadcasts, and turn conversations into revenue — all from one platform.");
        d.put("landing.hero_cta_primary", "Start Free Trial");
        d.put("landing.hero_cta_secondary", "View Pricing");
        d.put("landing.hero_trust_1", "No credit card required");
        d.put("landing.hero_trust_2", "14-day free trial");
        d.put("landing.hero_trust_3", "Official Meta Business APIs");

        d.put("landing.metrics_enabled", "1");
        d.put("landing.metric_1_value", "50M+");
        d.put("landing.metric_1_label", "Messages delivered");
        d.put("landing.metric_2_value", "12,000+");
        d.put("landing.metric_2_label", "Businesses");
        d.put("landing.metric_3_value", "99.9%");
        d.put("landing.metric_3_label", "Uptime SLA");
        d.put("landing.metric_4_value", "5x");
        d.put("landing.metric_4_label", "Higher open rates");

        d.put("landing.stats_enabled", "1");
        d.put("landing.stats_heading", "Trusted by 12,000+ businesses worldwide");
        d.put("landing.stats_1_label", "Acme");
        d.put("landing.stats_2_label", "TechStart");
        d.put("landing.stats_3_label", "GrowthLab");
        d.put("landing.stats_4_label", "Marketly");
        d.put("landing.stats_5_label", "SalesHQ");
        d.put("landing.stats_6_label", "ReachMore");

        d.put("landing.channels_enabled", "1");
        d.put("landing.channels_badge", "Omnichannel");
        d.put("landing.channels_title", "Meet customers where they already are");
        d.put("landing.channels_subtitle", "Connect every messaging channel to a single shared inbox — no more switching tabs.");
        d.put("landing.channel_1_key", "whatsapp");
        d.put("landing.channel_1_title", "WhatsApp Business");
        d.put("landing.channel_1_desc", "Official WhatsApp Cloud API. Send templates, broadcasts and 24/7 automated replies.");
        d.put("landing.channel_2_key", "messenger");
        d.put("landing.channel_2_title", "Facebook Messenger");
        d.put("landing.channel_2_desc", "Reply to Page messages, comments and story mentions from the same inbox.");
        d.put("landing.channel_3_key", "instagram");
        d.put("landing.channel_3_title", "Instagram DMs");
        d.put("landing.channel_3_desc", "Manage Instagram direct messages, comments and mentions in real time.");
        d.put("landing.channel_4_key", "sms");
        d.put("landing.channel_4_title", "SMS Campaigns");
        d.put("landing.channel_4_desc", "Reach customers instantly with high-deliverability SMS broadcasts and alerts.");
        d.put("landing.channel_5_key", "email");
        d.put("landing.channel_5_title", "Email");
        d.put("landing.channel_5_desc", "Send transactional and marketing email from the same contact timeline.");

        d.put("landing.problems_enabled", "1");
        d.put("landing.problems_title", "Sound familiar?");
        d.put("landing.problem_1", "Conversations scattered across WhatsApp, Messenger, Instagram and email");
        d.put("landing.problem_2", "Leads go cold while messages sit unanswered for hours");
        d.put("landing.problem_3", "No way to broadcast offers or automate follow-ups at scale");
        d.put("landing.problem_4", "Zero visibility into team performance or what is actually converting");
        d.put("landing.solution_title", "Hub Notification fixes all of it");
        d.put("landing.solution_desc", "One platform to capture, automate and close every conversation.");
        d.put("landing.solution_1", "Unified inbox for every channel, shared across your team");
        d.put("landing.solution_2", "AI chatbots and automations that reply 24/7");
        d.put("landing.solution_3", "Bulk broadcasts with smart scheduling and segmentation");
        d.put("landing.solution_4", "Real-time analytics on delivery, response time and revenue");

        d.put("landing.features_enabled", "1");
        d.put("landing.features_badge", "Features");
        d.put("landing.features_title", "Everything you need to win on every channel");
        d.put("landing.features_subtitle", "From a unified inbox to AI chatbots and broadcasts — one platform for the whole customer journey.");
        d.put("landing.feature_1_icon", "message-square");
        d.put("landing.feature_1_title", "Unified Team Inbox");
        d.put("landing.feature_1_desc", "Every WhatsApp, Messenger and Instagram conversation in one shared inbox with assignments, notes and canned replies.");
        d.put("landing.feature_2_icon", "cpu");
        d.put("landing.feature_2_title", "AI Chatbots");
        d.put("landing.feature_2_desc", "Train chatbots on your own docs and FAQs with RAG, and let AI answer instantly in any language.");
        d.put("landing.feature_3_icon", "zap");
        d.put("landing.feature_3_title", "No-Code Automation");
        d.put("landing.feature_3_desc", "Build visual workflows that route, tag and reply to messages automatically — no developer needed.");
        d.put("landing.feature_4_icon", "share-2");
        d.put("landing.feature_4_title", "Bulk Broadcasting");
        d.put("landing.feature_4_desc", "Send personalized WhatsApp and SMS campaigns to thousands of contacts with smart throttling.");
        d.put("landing.feature_5_icon", "users");
        d.put("landing.feature_5_title", "Contact CRM");
        d.put("landing.feature_5_desc", "Centralize every contact with full conversation history, custom fields, tags and segments.");
        d.put("landing.feature_6_icon", "trending-up");
        d.put("landing.feature_6_title", "Lead Generation");
        d.put("landing.feature_6_desc", "Capture and qualify leads automatically, score them, and never let a hot lead slip away.");
        d.put("landing.feature_7_icon", "layout");
        d.put("landing.feature_7_title", "E-commerce Sync");
        d.put("landing.feature_7_desc", "Connect your store to sync orders, send cart reminders and confirm deliveries over chat.");
        d.put("landing.feature_8_icon", "globe");
        d.put("landing.feature_8_title", "Social Scheduling");
        d.put("landing.feature_8_desc", "Plan and publish posts across your social accounts from one content calendar.");
        d.put("landing.feature_9_icon", "bar-chart-2");
        d.put("landing.feature_9_title", "Analytics & Reports");
        d.put("landing.feature_9_desc", "Track delivery, open and response rates, agent performance and campaign ROI in real time.");

        d.put("landing.howitworks_enabled", "1");
        d.put("landing.howitworks_badge", "Get started");
        d.put("landing.howitworks_title", "Live in minutes, not weeks");
        d.put("landing.howitworks_subtitle", "No code, no complex setup — connect a channel and go.");
        d.put("landing.step_1_title", "Connect Your Channels");
        d.put("landing.step_1_desc", "Link WhatsApp, Messenger and Instagram via official Meta APIs in a few clicks.");
        d.put("landing.step_2_title", "Import & Organize Contacts");
        d.put("landing.step_2_desc", "Upload a CSV or sync your CRM — we deduplicate and segment automatically.");
        d.put("landing.step_3_title", "Automate & Grow");
        d.put("landing.step_3_desc", "Launch broadcasts, set up AI chatbots, and watch conversations turn into customers.");

        d.put("landing.integrations_strip_enabled", "1");
        d.put("landing.integrations_strip_title", "Works with the tools you already use");
        d.put("landing.integrations_strip_subtitle", "Connect Hub Notification to 100+ apps via native integrations, webhooks and our REST API.");

        d.put("landing.why_enabled", "1");
        d.put("landing.why_badge", "Why Hub Notification");
        d.put("landing.why_title", "Built for teams who need results");
        d.put("landing.why_subtitle", "Powerful enough for enterprises, simple enough for everyone.");
        d.put("landing.why_1_icon", "shield-check");
        d.put("landing.why_1_title", "Official & Compliant");
        d.put("landing.why_1_desc", "Built on official Meta Business APIs — no bans, no grey-area hacks.");
        d.put("landing.why_2_icon", "zap");
        d.put("landing.why_2_title", "Lightning Fast");
        d.put("landing.why_2_desc", "Messages delivered in seconds on enterprise-grade infrastructure.");
        d.put("landing.why_3_icon", "trending-up");
        d.put("landing.why_3_title", "Higher Engagement");
        d.put("landing.why_3_desc", "Chat gets up to 5x the open rate of email — meet customers where they reply.");
        d.put("landing.why_4_icon", "globe");
        d.put("landing.why_4_title", "Multi-Language");
        d.put("landing.why_4_desc", "Serve customers in any language with built-in localization and AI translation.");
        d.put("landing.why_5_icon", "users");
        d.put("landing.why_5_title", "Team Collaboration");
        d.put("landing.why_5_desc", "Assign chats, leave internal notes, and never send a double reply.");
        d.put("landing.why_6_icon", "server");
        d.put("landing.why_6_title", "99.9% Uptime");
        d.put("landing.why_6_desc", "Reliable, secure and ready to scale with your business.");

        d.put("landing.security_enabled", "1");
        d.put("landing.security_badge", "Security & Compliance");
        d.put("landing.security_title", "Enterprise-grade security by default");
        d.put("landing.security_subtitle", "Your data and your customers' trust are protected at every layer.");
        d.put("landing.security_1_icon", "shield-check");
        d.put("landing.security_1_title", "End-to-End Encryption");
        d.put("landing.security_1_desc", "Data encrypted in transit and at rest with industry-standard protocols.");
        d.put("landing.security_2_icon", "check-circle");
        d.put("landing.security_2_title", "GDPR Compliant");
        d.put("landing.security_2_desc", "Data-processing controls, consent tracking and the right to be forgotten.");
        d.put("landing.security_3_icon", "users");
        d.put("landing.security_3_title", "Role-Based Access");
        d.put("landing.security_3_desc", "Granular permissions, 2FA and audit logs keep your workspace secure.");
        d.put("landing.security_4_icon", "server");
        d.put("landing.security_4_title", "Reliable Infrastructure");
        d.put("landing.security_4_desc", "Redundant, monitored systems with 99.9% uptime and automated backups.");

        d.put("landing.testimonials_enabled", "1");
        d.put("landing.testimonials_badge", "Testimonials");
        d.put("landing.testimonials_title", "Loved by modern teams");
        d.put("landing.testimonials_subtitle", "See what businesses are saying about Hub Notification.");
        d.put("landing.testimonial_1_name", "Sarah Johnson");
        d.put("landing.testimonial_1_role", "Marketing Manager, Acme Corp");
        d.put("landing.testimonial_1_text", "We tripled our lead response rate in the first week. The AI chatbot handles the repetitive questions so my team can focus on closing.");
        d.put("landing.testimonial_1_avatar", "");
        d.put("landing.testimonial_2_name", "James Lee");
        d.put("landing.testimonial_2_role", "Founder, TechStart");
        d.put("landing.testimonial_2_text", "Setup took 10 minutes. By the end of the day we were running our first WhatsApp broadcast to 8,000 contacts.");
        d.put("landing.testimonial_2_avatar", "");
        d.put("landing.testimonial_3_name", "Maria Santos");
        d.put("landing.testimonial_3_role", "Head of Growth, GrowthLab");
        d.put("landing.testimonial_3_text", "The unified inbox changed how our team handles support across WhatsApp and Instagram. Night and day difference.");
        d.put("landing.testimonial_3_avatar", "");
        d.put("landing.testimonial_4_name", "David Okafor");
        d.put("landing.testimonial_4_role", "Operations Lead, ReachMore");
        d.put("landing.testimonial_4_text", "Automations save us 30+ hours a week. It is like adding three team members who never sleep.");
        d.put("landing.testimonial_4_avatar", "");
        d.put("landing.testimonial_5_name", "Aisha Rahman");
        d.put("landing.testimonial_5_role", "E-commerce Owner, Marketly");
        d.put("landing.testimonial_5_text", "Cart reminders over WhatsApp recovered 22% of abandoned checkouts. It paid for itself in a week.");
        d.put("landing.testimonial_5_avatar", "");
        d.put("landing.testimonial_6_name", "Tom Becker");
        d.put("landing.testimonial_6_role", "Customer Success, SalesHQ");
        d.put("landing.testimonial_6_text", "Finally one place for every conversation. Our average first response time dropped from hours to minutes.");
        d.put("landing.testimonial_6_avatar", "");

        d.put("landing.faq_enabled", "1");
        d.put("landing.faq_badge", "FAQ");
        d.put("landing.faq_title", "Frequently Asked Questions");
        d.put("landing.faq_subtitle", "Everything you need to know about Hub Notification.");
        d.put("landing.faq_1_q", "Which channels does Hub Notification support?");
        d.put("landing.faq_1_a", "Connect WhatsApp Business, Facebook Messenger and Instagram DMs into one inbox, plus SMS and email broadcasting — all from a single dashboard.");
        d.put("landing.faq_2_q", "Do I need a WhatsApp Business API account?");
        d.put("landing.faq_2_a", "Yes — Hub Notification connects through the official Meta WhatsApp Cloud API. Our guided setup walks you through it in minutes.");
        d.put("landing.faq_3_q", "Can I try Hub Notification before paying?");
        d.put("landing.faq_3_a", "Absolutely. Every plan includes a 14-day free trial with no credit card required.");
        d.put("landing.faq_4_q", "Do the AI chatbots understand my business?");
        d.put("landing.faq_4_a", "Yes. Train chatbots on your own documents, FAQs and product catalog using RAG so answers are accurate and on-brand.");
        d.put("landing.faq_5_q", "Is my data secure and compliant?");
        d.put("landing.faq_5_a", "We use end-to-end encryption, role-based access and are fully GDPR compliant. We never sell or share your data.");

        d.put("landing.cta_enabled", "1");
        d.put("landing.cta_title", "Ready to win every conversation?");
        d.put("landing.cta_subtitle", "Join 12,000+ businesses growing faster with Hub Notification. Start free — no credit card required.");
        d.put("landing.cta_primary", "Start Free Trial");
        d.put("landing.cta_secondary", "Talk to Sales");

        return d;
    }

    /** Every default except the master `page_enabled` toggle (read separately). */
    public Map<String, Object> getPublicSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : defaults().entrySet()) {
            if ("landing.page_enabled".equals(entry.getKey())) {
                continue;
            }
            String value = systemSettingRepository.findByKey(entry.getKey())
                    .map(s -> s.getValue())
                    .orElse(entry.getValue());
            result.put(entry.getKey(), value);
        }
        return result;
    }
}
