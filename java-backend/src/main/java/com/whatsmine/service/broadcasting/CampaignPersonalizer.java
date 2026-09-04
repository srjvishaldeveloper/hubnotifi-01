package com.whatsmine.service.broadcasting;

import com.whatsmine.model.Contact;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CampaignPersonalizer {

    public String renderText(String template, Contact contact, Map<String, String> context) {
        if (template == null || template.isEmpty() || !template.contains("{{")) {
            return template != null ? template : "";
        }

        String fullName = (contact.getFirstName() != null ? contact.getFirstName() : "") +
                (contact.getLastName() != null && !contact.getLastName().isBlank() ? " " + contact.getLastName() : "");
        template = template.replace("{{contact.name}}", fullName.trim());

        // {{contact.first_name}}
        template = template.replace("{{contact.first_name}}", contact.getFirstName() != null ? contact.getFirstName() : "");

        // {{contact.last_name}}
        template = template.replace("{{contact.last_name}}", contact.getLastName() != null ? contact.getLastName() : "");

        // {{contact.email}}
        template = template.replace("{{contact.email}}", contact.getEmail() != null ? contact.getEmail() : "");

        // {{contact.phone_e164}}
        template = template.replace("{{contact.phone_e164}}", contact.getPhoneE164() != null ? contact.getPhoneE164() : "");

        // {{contact.country}}
        template = template.replace("{{contact.country}}", contact.getCountry() != null ? contact.getCountry() : "");

        // {{contact.language}}
        template = template.replace("{{contact.language}}", contact.getLanguage() != null ? contact.getLanguage() : "");

        // {{context.<key>}}
        if (context != null && !context.isEmpty()) {
            Pattern contextPattern = Pattern.compile("\\{\\{\\s*context\\.([a-zA-Z0-9_]+)\\s*\\}\\}");
            Matcher matcher = contextPattern.matcher(template);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1);
                String val = context.getOrDefault(key, "");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
            }
            matcher.appendTail(sb);
            template = sb.toString();
        }

        return template;
    }

    public String renderText(String template, Contact contact) {
        return renderText(template, contact, Collections.emptyMap());
    }

    public List<Map<String, Object>> availableContactTokens() {
        return List.of(
                Map.of("key", "{{contact.first_name}}", "label", "First name"),
                Map.of("key", "{{contact.last_name}}", "label", "Last name"),
                Map.of("key", "{{contact.name}}", "label", "Full name"),
                Map.of("key", "{{contact.email}}", "label", "Email"),
                Map.of("key", "{{contact.phone_e164}}", "label", "Phone (E.164)"),
                Map.of("key", "{{contact.country}}", "label", "Country"),
                Map.of("key", "{{contact.language}}", "label", "Language")
        );
    }
}
