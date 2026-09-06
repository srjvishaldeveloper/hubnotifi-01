package com.whatsmine.controller.broadcasting;

import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ai.LlmGateway;
import com.whatsmine.service.ai.llm.LlmResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ports PHP's Broadcasting\Http\Controllers\EmailAiController exactly:
 * AI-generated email copy and subject-line suggestions for the campaign
 * composer, via the same real LlmGateway every other AI feature uses.
 */
@RestController
@RequestMapping("/app/broadcasts/campaigns")
public class EmailAiController {

    private final LlmGateway llmGateway;

    public EmailAiController(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    @PostMapping("/improve-subject")
    public ResponseEntity<Map<String, Object>> improveSubject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        String subject = str(body.get("subject"));
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "A subject is required."));
        }
        String bodyText = str(body.get("body"));

        String bodySnippet = "";
        if (bodyText != null && !bodyText.isBlank()) {
            String plain = stripTags(bodyText);
            bodySnippet = "\nEmail body context (first 300 chars): " + plain.substring(0, Math.min(300, plain.length()));
        }

        String systemPrompt = """
                You are an expert email copywriter. Your only task is to write short email subject lines — the text that appears in an inbox before opening an email.

                Output EXACTLY 3 lines numbered like this, and nothing else:
                1. <subject line>
                2. <subject line>
                3. <subject line>

                ═══ EXAMPLE INPUT ═══
                Current subject: Welcome to Acme

                ═══ EXAMPLE OUTPUT ═══
                1. Your Acme journey starts today 🎉
                2. Ready to get started? Your account awaits
                3. Welcome, {{contact.first_name}} — here's what's next

                ═══ EXAMPLE INPUT ═══
                Current subject: 50% off this weekend only

                ═══ EXAMPLE OUTPUT ═══
                1. Save 50% before Sunday — don't miss out
                2. Is this the best deal we've ever offered?
                3. 50% off ends Sunday — grab yours now

                Rules:
                - Each subject line must be under 60 characters of plain text.
                - Line 1: highlight the main benefit or offer.
                - Line 2: curiosity or question that makes the reader want to open.
                - Line 3: direct, urgent, and clear.
                - A subject line is a SHORT PHRASE — never a sentence explaining strategy.
                - Preserve any {{contact.field}} tokens if present in the original.
                - No markdown, no bold (**), no explanations — just the 3 subject lines.
                """;

        String userMessage = "Current subject: " + subject + bodySnippet;

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            );
            LlmResponse response = llmGateway.chat(userDetails.getWorkspaceId(), messages, Map.of("max_tokens", 200));
            List<String> suggestions = parseSubjectSuggestions(response.content() != null ? response.content().trim() : "");

            if (suggestions.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Could not generate subject lines. Please try again."));
            }
            return ResponseEntity.ok(Map.of("suggestions", suggestions.subList(0, Math.min(3, suggestions.size()))));
        } catch (RuntimeException e) {
            // LlmManager.forWorkspace() throws a plain RuntimeException when no
            // provider is configured — a config problem, not a server error.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No AI provider configured. Set one up in AI → Providers."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "AI suggestion failed: " + e.getMessage()));
        }
    }

    @PostMapping("/generate-email")
    public ResponseEntity<Map<String, Object>> generate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        String prompt = str(body.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "A prompt is required."));
        }
        String campaignName = str(body.get("campaign_name"));
        String tone = str(body.get("tone"));
        if (tone == null || tone.isBlank()) tone = "professional";

        String campaignCtx = campaignName != null && !campaignName.isBlank() ? "Campaign name: \"" + campaignName + "\"." : "";

        String systemPrompt = """
                You are an email copywriter. Your ONLY job is to write the text of a single marketing or transactional email that will be sent to recipients.

                You must respond with EXACTLY two sections — nothing else before, between, or after:

                SUBJECT: <one subject line, plain text, max 60 chars>
                BODY:
                <email body using only HTML tags with inline style= attributes>

                ═══ EXAMPLE INPUT ═══
                Write a welcome email for new customers who just signed up.

                ═══ EXAMPLE OUTPUT ═══
                SUBJECT: Welcome aboard, {{contact.first_name}}!
                BODY:
                <p style="font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;margin:0 0 14px;">Hi {{contact.first_name}},</p>
                <p style="font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;margin:0 0 14px;">Welcome! We're thrilled to have you with us. Your account is ready and you can start exploring right away.</p>
                <p style="font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;margin:0 0 20px;">Here's what you can do first:</p>
                <ul style="font-family:sans-serif;font-size:15px;line-height:1.8;color:#333;margin:0 0 20px;padding-left:20px;">
                <li>Complete your profile</li>
                <li>Browse our features</li>
                <li>Reach out if you need help</li>
                </ul>
                <div style="text-align:center;margin:24px 0;">
                <a href="#" style="display:inline-block;padding:13px 28px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-family:sans-serif;font-size:15px;font-weight:600;">Get Started</a>
                </div>
                <p style="font-family:sans-serif;font-size:14px;line-height:1.6;color:#6b7280;margin:0;">Warm regards,<br>The Team</p>
                ═══ END EXAMPLE ═══

                Rules you MUST follow:
                - Write the email body as if it will be sent directly to a recipient. It must read like a real email — greeting, content, CTA, sign-off.
                - Do NOT write marketing strategies, bullet-point plans, campaign ideas, or advice. Write the actual email text.
                - Use ONLY HTML tags with inline style= attributes. No markdown (no ##, no **, no dashes for lists).
                - Do NOT include <html>, <head>, or <body> tags.
                - Tone: %s.
                - Use {{contact.first_name}} naturally for personalisation.
                - Keep it concise: 150–300 words in the body.
                """.formatted(tone);

        String userContent = (campaignCtx + " " + prompt).trim();

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            );
            LlmResponse response = llmGateway.chat(userDetails.getWorkspaceId(), messages, Map.of("max_tokens", 2000));
            Map<String, String> parsed = parseEmailResponse(response.content() != null ? response.content() : "");

            if (parsed != null && looksLikeStrategy(parsed.get("body"))) {
                List<Map<String, String>> retryMessages = List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", "Write the actual email (not a strategy or plan). " + userContent)
                );
                LlmResponse retryResponse = llmGateway.chat(userDetails.getWorkspaceId(), retryMessages, Map.of("max_tokens", 2000));
                Map<String, String> retryParsed = parseEmailResponse(retryResponse.content() != null ? retryResponse.content() : "");
                if (retryParsed != null && !looksLikeStrategy(retryParsed.get("body"))) {
                    parsed = retryParsed;
                }
            }

            if (parsed == null) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "AI returned an unexpected format. Try rephrasing your prompt."));
            }
            return ResponseEntity.ok(Map.of("subject", parsed.get("subject"), "body", parsed.get("body")));
        } catch (RuntimeException e) {
            // LlmManager.forWorkspace() throws a plain RuntimeException when no
            // provider is configured — a config problem, not a server error.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No AI provider configured. Set one up in AI → Providers."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "AI generation failed: " + e.getMessage()));
        }
    }

    // ── parsing helpers, ported 1:1 from PHP ────────────────────────────

    private Map<String, String> parseEmailResponse(String raw) {
        raw = raw.trim();
        raw = raw.replaceFirst("(?i)^```[a-z]*\\s*", "");
        raw = raw.replaceFirst("\\s*```\\s*$", "").trim();

        Matcher m = Pattern.compile("SUBJECT:\\s*(.+?)\\s*\\nBODY:\\s*\\n?([\\s\\S]+)$", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (m.find()) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("subject", m.group(1).trim());
            out.put("body", ensureHtml(m.group(2).trim()));
            return out;
        }

        String[] lines = raw.split("\n");
        List<String> nonEmpty = new ArrayList<>();
        for (String l : lines) {
            if (!l.trim().isEmpty()) nonEmpty.add(l);
        }
        if (nonEmpty.size() >= 2) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("subject", stripTags(nonEmpty.get(0).trim()));
            StringBuilder rest = new StringBuilder();
            for (int i = 1; i < nonEmpty.size(); i++) {
                if (i > 1) rest.append("\n");
                rest.append(nonEmpty.get(i));
            }
            out.put("body", ensureHtml(rest.toString().trim()));
            return out;
        }

        return null;
    }

    private boolean looksLikeStrategy(String body) {
        if (body == null) return false;
        String plain = stripTags(body).toLowerCase();
        String excerpt = plain.substring(0, Math.min(400, plain.length()));

        String[] strategySignals = {
                "marketing strateg", "campaign strateg", "suggested strateg", "campaign detail",
                "social media post", "influencer", "target audience", "key performance",
                "marketing plan", "content calendar", "engagement rate", "call to action strategy",
                "campaign goal"
        };

        int hits = 0;
        for (String signal : strategySignals) {
            if (excerpt.contains(signal)) hits++;
        }

        boolean hasGreeting = Pattern.compile("\\b(hi|hello|dear|hey|greetings|good (morning|afternoon|evening))\\b", Pattern.CASE_INSENSITIVE)
                .matcher(excerpt).find();

        return hits >= 2 || (hits >= 1 && !hasGreeting);
    }

    private List<String> parseSubjectSuggestions(String text) {
        List<String> results = new ArrayList<>();

        List<String> candidates = new ArrayList<>();
        Matcher numbered = Pattern.compile("^\\s*\\d+[.)]\\s*(.+)", Pattern.MULTILINE).matcher(text);
        while (numbered.find()) {
            candidates.add(numbered.group(1));
        }

        if (candidates.isEmpty()) {
            for (String line : text.split("\n")) {
                if (!line.trim().isEmpty()) candidates.add(line);
            }
        }

        String[] strategyWords = {
                "utilize", "platforms like", "showcase", "testimonial", "influencer", "newsletter",
                "bi-weekly", "collaboration", "strategies", "engagement rate", "target audience", "campaign goal"
        };

        for (String line : candidates) {
            String clean = line.replaceAll("\\*{1,2}(.*?)\\*{1,2}", "$1");
            clean = clean.strip();
            clean = stripEdgeChars(clean, " \t\n\r\0\"'*-•");

            if (clean.isEmpty() || clean.length() > 80) continue;

            String lower = clean.toLowerCase();
            boolean isStrategy = false;
            for (String word : strategyWords) {
                if (lower.contains(word)) { isStrategy = true; break; }
            }
            if (isStrategy) continue;

            results.add(clean);
        }

        return results;
    }

    private String ensureHtml(String content) {
        if (Pattern.compile("<[a-z][^>]*>", Pattern.CASE_INSENSITIVE).matcher(content).find()) {
            return content;
        }

        String html = content;

        Matcher heading = Pattern.compile("^(#{1,3})\\s+(.+)$", Pattern.MULTILINE).matcher(html);
        StringBuilder sb = new StringBuilder();
        while (heading.find()) {
            int level = heading.group(1).length();
            String size = switch (level) { case 1 -> "22px"; case 2 -> "18px"; default -> "16px"; };
            heading.appendReplacement(sb, Matcher.quoteReplacement(
                    "<h" + level + " style=\"font-family:sans-serif;font-size:" + size + ";font-weight:700;color:#111;margin:16px 0 8px;\">" + heading.group(2) + "</h" + level + ">"));
        }
        heading.appendTail(sb);
        html = sb.toString();

        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");

        Matcher listBlock = Pattern.compile("^(\\s*[-*•]\\s+.+(\\n\\s*[-*•]\\s+.+)*)", Pattern.MULTILINE).matcher(html);
        StringBuilder sb2 = new StringBuilder();
        while (listBlock.find()) {
            String[] lines = listBlock.group(0).split("\n");
            StringBuilder items = new StringBuilder();
            for (String line : lines) {
                String text = line.trim().replaceFirst("^[-*•]\\s+", "").trim();
                items.append("<li style=\"margin-bottom:6px;\">").append(text).append("</li>");
            }
            listBlock.appendReplacement(sb2, Matcher.quoteReplacement(
                    "<ul style=\"font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;margin:0 0 12px;padding-left:20px;\">" + items + "</ul>"));
        }
        listBlock.appendTail(sb2);
        html = sb2.toString();

        String[] paragraphs = html.trim().split("\n{2,}");
        StringBuilder result = new StringBuilder();
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (Pattern.compile("^<[a-z]", Pattern.CASE_INSENSITIVE).matcher(para).find()) {
                result.append(para).append("\n");
            } else {
                para = para.replace("\n", "<br>");
                result.append("<p style=\"font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;margin:0 0 12px;\">").append(para).append("</p>\n");
            }
        }

        return result.toString().trim();
    }

    private String stripEdgeChars(String s, String chars) {
        int start = 0, end = s.length();
        while (start < end && chars.indexOf(s.charAt(start)) >= 0) start++;
        while (end > start && chars.indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(start, end);
    }

    private String stripTags(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", "");
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
