package com.whatsmine.service.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.service.IntegrationCredentialsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * SMS via Twilio's REST API — the system-level "sms_twilio" integration
 * (Admin > Integrations), following the exact same IntegrationConfig
 * pattern as the Meta App / WhatsApp credentials. No credentials
 * configured = the send throws, loudly, rather than fabricating a fake
 * success.
 */
@Service
public class SmsApiClient {

    private static final Logger log = LoggerFactory.getLogger(SmsApiClient.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String PROVIDER = "sms_twilio";

    private final IntegrationCredentialsService integrationCredentialsService;
    private final ObjectMapper objectMapper;

    public SmsApiClient(IntegrationCredentialsService integrationCredentialsService, ObjectMapper objectMapper) {
        this.integrationCredentialsService = integrationCredentialsService;
        this.objectMapper = objectMapper;
    }

    /** Returns the Twilio message SID on success. Throws IllegalStateException on missing config or API error. */
    @SuppressWarnings("unchecked")
    public String sendText(String to, String body) {
        Map<String, String> creds = integrationCredentialsService.getCredentials(PROVIDER);
        String accountSid = creds.get("account_sid");
        String authToken = creds.get("auth_token");
        String fromNumber = creds.get("from_number");

        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("No SMS provider configured — add a Twilio Account SID/Auth Token under Admin > Integrations > SMS (Twilio).");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException("SMS provider has no From number configured.");
        }

        try {
            String form = "To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);

            String basicAuth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);

            if (response.statusCode() >= 400) {
                String message = String.valueOf(parsed.getOrDefault("message", "HTTP " + response.statusCode()));
                log.warn("SMS send failed ({}): {}", response.statusCode(), message);
                throw new IllegalStateException("Twilio API error: " + message);
            }

            return String.valueOf(parsed.get("sid"));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Twilio API call failed: {}", e.getMessage());
            throw new IllegalStateException("Twilio API call failed: " + e.getMessage(), e);
        }
    }
}
