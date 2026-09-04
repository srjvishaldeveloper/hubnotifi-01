package com.whatsmine.queue.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappTemplate;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles TemplateSyncJob — mirrors Laravel TemplateSyncJob.
 * When wabaDbId is present: syncs templates for that WABA only.
 * When dispatched from scheduler without wabaDbId: no-op (scheduler dispatches per-WABA).
 * Fetches templates from Meta Graph API and upserts into whatsapp_templates.
 * Tries: 3 | Queue: whatsapp
 */
@Component
public class TemplateSyncJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateSyncJobHandler.class);

    private static final String GRAPH_BASE = "https://graph.facebook.com/v18.0";

    private final WhatsappBusinessAccountRepository wabaRepository;
    private final WhatsappTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.api.base-url:https://graph.facebook.com/v18.0}")
    private String graphBaseUrl;

    public TemplateSyncJobHandler(WhatsappBusinessAccountRepository wabaRepository,
                                  WhatsappTemplateRepository templateRepository,
                                  ObjectMapper objectMapper) {
        this.wabaRepository = wabaRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getJobType() {
        return "TemplateSyncJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number wabaDbIdNum = (Number) data.get("wabaDbId");
        if (wabaDbIdNum == null) {
            log.info("TemplateSyncJob: no wabaDbId provided, skipping (scheduler dispatches per-WABA)");
            return;
        }
        long wabaDbId = wabaDbIdNum.longValue();
        log.info("TemplateSyncJob: starting for wabaDbId={}", wabaDbId);

        WhatsappBusinessAccount waba = wabaRepository.findById(wabaDbId).orElse(null);
        if (waba == null) {
            log.warn("TemplateSyncJob: WABA {} not found, skipping", wabaDbId);
            return;
        }

        // Extract access token from credentials JSON
        String accessToken = extractAccessToken(waba.getCredentials());
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("TemplateSyncJob: no access token for wabaDbId={}, workspaceId={}", wabaDbId, waba.getWorkspaceId());
            return;
        }

        List<Map<String, Object>> templates = fetchTemplates(waba.getWabaId(), accessToken);
        if (templates == null) {
            log.warn("TemplateSyncJob: failed to fetch templates for wabaId={}", waba.getWabaId());
            return;
        }

        int upserted = 0;
        for (Map<String, Object> tpl : templates) {
            String name = (String) tpl.getOrDefault("name", "");
            String language = (String) tpl.getOrDefault("language", "en");
            if (name.isBlank()) continue;

            Optional<WhatsappTemplate> existing = templateRepository
                    .findByWorkspaceIdAndNameAndLanguage(waba.getWorkspaceId(), name, language);

            WhatsappTemplate record = existing.orElse(new WhatsappTemplate());
            record.setWorkspaceId(waba.getWorkspaceId());
            record.setWabaId(waba.getWabaId());
            record.setName(name);
            record.setLanguage(language);
            record.setCategory((String) tpl.getOrDefault("category", "MARKETING"));
            record.setStatus((String) tpl.getOrDefault("status", "PENDING"));
            if (tpl.get("id") instanceof String metaId) record.setMetaTemplateId(metaId);
            if (tpl.get("rejection_reason") instanceof String rr) record.setRejectionReason(rr);
            if (tpl.get("components") != null) {
                try {
                    record.setComponents(objectMapper.writeValueAsString(tpl.get("components")));
                } catch (Exception ignored) {}
            }
            templateRepository.save(record);
            upserted++;
        }
        log.info("TemplateSyncJob: upserted {} templates for wabaDbId={}", upserted, wabaDbId);
    }

    @SuppressWarnings("unchecked")
    private String extractAccessToken(String credentialsJson) {
        if (credentialsJson == null || credentialsJson.isBlank()) return null;
        try {
            Map<String, Object> creds = objectMapper.readValue(credentialsJson, new TypeReference<>() {});
            Object token = creds.get("access_token");
            return token instanceof String s ? s : null;
        } catch (Exception e) {
            log.debug("TemplateSyncJob: could not parse credentials JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTemplates(String wabaId, String accessToken) {
        try {
            RestTemplate rest = new RestTemplate();
            String url = graphBaseUrl + "/" + wabaId + "/message_templates?limit=250&fields=name,language,category,status,components,rejection_reason";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return List.of();
            Object data = body.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : List.of();
        } catch (Exception e) {
            log.warn("TemplateSyncJob: HTTP error fetching templates for wabaId={}: {}", wabaId, e.getMessage());
            return null;
        }
    }

    @Override
    public int getMaxTries() {
        return 3;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60, 300, 600};
    }
}
