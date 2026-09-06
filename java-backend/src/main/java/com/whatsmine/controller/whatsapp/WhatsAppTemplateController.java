package com.whatsmine.controller.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappPhoneNumber;
import com.whatsmine.model.WhatsappTemplate;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappPhoneNumberRepository;
import com.whatsmine.repository.WhatsappTemplateRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.IntegrationCredentialsService;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ports php/app/Modules/Whatsapp/Http/Controllers/WhatsappTemplateController.php.
 * The previous Java version rendered a "Client/WhatsApp/Templates" component
 * that doesn't exist anywhere in the shared frontend (the real pages are
 * Whatsapp/Templates/Index and Whatsapp/Templates/Editor) and store()/sync()
 * never actually called Meta — both fixed here alongside the documented
 * missing edit/update/upload-media endpoints.
 */
@RestController
@RequestMapping("/app/whatsapp/templates")
public class WhatsAppTemplateController {

    private final WhatsappTemplateRepository templateRepository;
    private final WhatsappBusinessAccountRepository wabaRepository;
    private final WhatsappPhoneNumberRepository phoneNumberRepository;
    private final WhatsAppApiClient whatsAppApiClient;
    private final IntegrationCredentialsService integrationCredentialsService;
    private final ObjectMapper objectMapper;

    public WhatsAppTemplateController(
            WhatsappTemplateRepository templateRepository,
            WhatsappBusinessAccountRepository wabaRepository,
            WhatsappPhoneNumberRepository phoneNumberRepository,
            WhatsAppApiClient whatsAppApiClient,
            IntegrationCredentialsService integrationCredentialsService,
            ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.wabaRepository = wabaRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.whatsAppApiClient = whatsAppApiClient;
        this.integrationCredentialsService = integrationCredentialsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(value = "phone_number_id", required = false) String phoneNumberId) {
        Long workspaceId = userDetails.getWorkspaceId();

        List<Map<String, Object>> phoneNumbers = phoneNumbersFor(workspaceId);

        List<WhatsappTemplate> templates = templateRepository.findByWorkspaceId(workspaceId);
        List<Map<String, Object>> filtered = new ArrayList<>();
        String matchedWabaId = null;
        if (phoneNumberId != null && !phoneNumberId.isBlank()) {
            matchedWabaId = phoneNumbers.stream()
                    .filter(p -> phoneNumberId.equals(p.get("phone_number_id")))
                    .map(p -> (String) p.get("waba_id")).findFirst().orElse(null);
        }
        for (WhatsappTemplate t : templates) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(t.getStatus())) continue;
            if (search != null && !search.isBlank() && (t.getName() == null || !t.getName().toLowerCase().contains(search.toLowerCase())))
                continue;
            if (matchedWabaId != null && !matchedWabaId.equals(t.getWabaId())) continue;
            filtered.add(templateSummary(t));
        }
        filtered.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("search", search);
        filters.put("phone_number_id", phoneNumberId);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("templates", filtered);
        props.put("phoneNumbers", phoneNumbers);
        props.put("filters", filters);
        return Inertia.render("Whatsapp/Templates/Index", props);
    }

    @GetMapping("/create")
    public Object create(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("template", null);
        props.put("phoneNumbers", phoneNumbersFor(userDetails.getWorkspaceId()));
        return Inertia.render("Whatsapp/Templates/Editor", props);
    }

    @GetMapping("/{id}/edit")
    public Object edit(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        WhatsappTemplate template = requireOwnedTemplate(userDetails, id);

        Map<String, Object> templateProps = new LinkedHashMap<>();
        templateProps.put("id", template.getId());
        templateProps.put("name", template.getName());
        templateProps.put("language", template.getLanguage());
        templateProps.put("category", template.getCategory());
        templateProps.put("status", template.getStatus());
        templateProps.put("components", parseComponents(template.getComponents()));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("template", templateProps);
        props.put("phoneNumbers", phoneNumbersFor(userDetails.getWorkspaceId()));
        return Inertia.render("Whatsapp/Templates/Editor", props);
    }

    @PostMapping
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Long workspaceId = userDetails.getWorkspaceId();

        WhatsappBusinessAccount waba = resolveWaba(workspaceId, str(body.get("phone_number_id")));
        if (waba == null) {
            Inertia.flashError(session, "Connect a WhatsApp Business Account before creating a template.");
            return Inertia.redirect("/app/whatsapp/templates");
        }

        List<Map<String, Object>> components = componentsFrom(body.get("components"));
        String multiplicityError = assertComponentMultiplicity(components);
        if (multiplicityError != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("errors", Map.of("components", multiplicityError)));
        }

        String name = str(body.get("name"));
        String language = str(body.getOrDefault("language", "en"));
        String category = str(body.getOrDefault("category", "MARKETING"));

        WhatsappTemplate template = new WhatsappTemplate();
        template.setWorkspaceId(workspaceId);
        template.setWabaId(waba.getWabaId());
        template.setName(name);
        template.setLanguage(language);
        template.setCategory(category);
        template.setStatus("PENDING");
        template.setComponents(writeJson(components));
        template = templateRepository.save(template);

        String token = metaAccessToken(waba);
        if (!token.isBlank()) {
            Map<String, Object> metaPayload = buildMetaPayload(name, language, category, components);
            Map<String, Object> resp = whatsAppApiClient.submitTemplate(waba.getWabaId(), metaPayload, token);
            if (Boolean.TRUE.equals(resp.get("_success"))) {
                template.setMetaTemplateId(str(resp.get("id")));
                templateRepository.save(template);
            } else {
                String metaError = metaErrorMessage(resp);
                template.setStatus("REJECTED");
                template.setRejectionReason(metaError);
                templateRepository.save(template);
                Inertia.flashError(session, "Template saved but Meta rejected it: " + metaError);
                return Inertia.redirect("/app/whatsapp/templates");
            }
        }

        Inertia.flashSuccess(session, "Template submitted to Meta for approval.");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    @PutMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        WhatsappTemplate template = requireOwnedTemplate(userDetails, id);

        List<Map<String, Object>> components = componentsFrom(body.get("components"));
        String multiplicityError = assertComponentMultiplicity(components);
        if (multiplicityError != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("errors", Map.of("components", multiplicityError)));
        }
        // Name and language are immutable on Meta once a template exists.
        String category = str(body.getOrDefault("category", template.getCategory()));

        template.setCategory(category);
        template.setComponents(writeJson(components));
        templateRepository.save(template);

        WhatsappBusinessAccount waba = wabaRepository.findByWorkspaceIdAndWabaId(userDetails.getWorkspaceId(), template.getWabaId()).orElse(null);
        String token = waba != null ? metaAccessToken(waba) : "";
        if (waba != null && !token.isBlank()) {
            Map<String, Object> metaPayload = buildMetaPayload(template.getName(), template.getLanguage(), category, components);
            Map<String, Object> resp = template.getMetaTemplateId() != null
                    ? whatsAppApiClient.editTemplate(template.getMetaTemplateId(), metaPayload, token)
                    : whatsAppApiClient.submitTemplate(waba.getWabaId(), metaPayload, token);

            if (Boolean.TRUE.equals(resp.get("_success"))) {
                template.setStatus("PENDING");
                template.setRejectionReason(null);
                if (template.getMetaTemplateId() == null) {
                    template.setMetaTemplateId(str(resp.get("id")));
                }
                templateRepository.save(template);
            } else {
                String metaError = metaErrorMessage(resp);
                template.setStatus("REJECTED");
                template.setRejectionReason(metaError);
                templateRepository.save(template);
                Inertia.flashError(session, "Template saved but Meta rejected the change: " + metaError);
                return Inertia.redirect("/app/whatsapp/templates");
            }
        }

        Inertia.flashSuccess(session, "Template updated and resubmitted to Meta for approval.");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        WhatsappTemplate template = requireOwnedTemplate(userDetails, id);

        String metaWarning = null;
        WhatsappBusinessAccount waba = wabaRepository.findByWorkspaceIdAndWabaId(userDetails.getWorkspaceId(), template.getWabaId()).orElse(null);
        if (waba != null) {
            String token = metaAccessToken(waba);
            if (!token.isBlank()) {
                try {
                    Map<String, Object> resp = whatsAppApiClient.deleteTemplate(waba.getWabaId(), template.getName(), token);
                    if (!Boolean.TRUE.equals(resp.get("_success"))) {
                        metaWarning = metaErrorMessage(resp);
                    }
                } catch (Exception e) {
                    metaWarning = e.getMessage();
                }
            }
        }

        String name = template.getName();
        templateRepository.delete(template);

        if (metaWarning != null) {
            Inertia.flashError(session, "Deleted \"" + name + "\" locally, but Meta reported: " + metaWarning);
        } else {
            Inertia.flashSuccess(session, "Template \"" + name + "\" deleted.");
        }
        return Inertia.redirect("/app/whatsapp/templates");
    }

    /** Uploads a header media file and returns the Meta resumable-upload handle. */
    @PostMapping("/upload-media")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        Long workspaceId = userDetails.getWorkspaceId();
        WhatsappBusinessAccount waba = wabaRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream().findFirst().orElse(null);
        if (waba == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Connect a WhatsApp Business Account first."));
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String format = mimeType.startsWith("image/") ? "IMAGE" : mimeType.startsWith("video/") ? "VIDEO"
                : "application/pdf".equals(mimeType) ? "DOCUMENT" : "IMAGE";

        String token = metaAccessToken(waba);
        String appId = integrationCredentialsService.getCredential("meta_app", "app_id");
        if (token.isBlank() || appId == null || appId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Missing Meta credentials (system user token or app ID)."));
        }

        try {
            String handle = whatsAppApiClient.resumableUpload(appId, token, file.getBytes(), mimeType);
            return ResponseEntity.ok(Map.of("handle", handle, "format", format));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sync")
    public Object sync(@AuthenticationPrincipal CustomUserDetails userDetails, HttpSession session) {
        Long workspaceId = userDetails.getWorkspaceId();
        WhatsappBusinessAccount waba = wabaRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream().findFirst().orElse(null);
        if (waba == null) {
            Inertia.flashError(session, "Connect a WhatsApp Business Account before syncing templates.");
            return Inertia.redirect("/app/whatsapp/templates");
        }

        String token = metaAccessToken(waba);
        if (token.isBlank()) {
            Inertia.flashError(session, "No access token available for this workspace.");
            return Inertia.redirect("/app/whatsapp/templates");
        }

        try {
            List<Map<String, Object>> remoteTemplates = whatsAppApiClient.fetchTemplates(waba.getWabaId(), token);
            for (Map<String, Object> tpl : remoteTemplates) {
                String name = str(tpl.get("name"));
                String language = str(tpl.get("language"));
                if (name == null || language == null) continue;

                WhatsappTemplate template = templateRepository.findByWorkspaceIdAndNameAndLanguage(workspaceId, name, language)
                        .orElseGet(WhatsappTemplate::new);
                template.setWorkspaceId(workspaceId);
                template.setWabaId(waba.getWabaId());
                template.setName(name);
                template.setLanguage(language);
                template.setCategory(str(tpl.getOrDefault("category", "MARKETING")));
                template.setStatus(str(tpl.getOrDefault("status", "PENDING")));
                template.setComponents(writeJson(tpl.get("components") instanceof List ? tpl.get("components") : List.of()));
                template.setRejectionReason(str(tpl.get("rejection_reason")));
                template.setMetaTemplateId(str(tpl.get("id")));
                templateRepository.save(template);
            }
        } catch (Exception e) {
            Inertia.flashError(session, "Could not sync templates from Meta: " + e.getMessage());
            return Inertia.redirect("/app/whatsapp/templates");
        }

        long count = templateRepository.findByWorkspaceId(workspaceId).size();
        Inertia.flashSuccess(session, "Synced templates from Meta (" + count + " in your workspace).");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private WhatsappTemplate requireOwnedTemplate(CustomUserDetails userDetails, Long id) {
        WhatsappTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!template.getWorkspaceId().equals(userDetails.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return template;
    }

    private List<Map<String, Object>> phoneNumbersFor(Long workspaceId) {
        Map<Long, String> wabaIdMap = new LinkedHashMap<>();
        for (WhatsappBusinessAccount w : wabaRepository.findByWorkspaceIdOrderByIdDesc(workspaceId)) {
            wabaIdMap.put(w.getId(), w.getWabaId());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long wabaDbId : wabaIdMap.keySet()) {
            for (WhatsappPhoneNumber p : phoneNumberRepository.findByWabaIdFk(wabaDbId)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("phone_number_id", p.getPhoneNumberId());
                row.put("display_phone", p.getDisplayPhone());
                row.put("verified_name", p.getVerifiedName());
                row.put("waba_id", wabaIdMap.get(wabaDbId));
                result.add(row);
            }
        }
        return result;
    }

    private WhatsappBusinessAccount resolveWaba(Long workspaceId, String phoneNumberId) {
        if (phoneNumberId != null && !phoneNumberId.isBlank()) {
            for (WhatsappBusinessAccount waba : wabaRepository.findByWorkspaceIdOrderByIdDesc(workspaceId)) {
                boolean matches = phoneNumberRepository.findByWabaIdFk(waba.getId()).stream()
                        .anyMatch(p -> phoneNumberId.equals(p.getPhoneNumberId()));
                if (matches) return waba;
            }
        }
        return wabaRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream().findFirst().orElse(null);
    }

    /** Per-WABA override (credentials.system_user_token) first, else the system-level Meta App System User Token. */
    @SuppressWarnings("unchecked")
    private String metaAccessToken(WhatsappBusinessAccount waba) {
        String raw = waba.getCredentials();
        if (raw != null && !raw.isBlank()) {
            try {
                Map<String, Object> creds = objectMapper.readValue(raw, Map.class);
                Object token = creds.get("system_user_token");
                if (token != null && !token.toString().isBlank()) {
                    return token.toString();
                }
            } catch (Exception ignored) { }
        }
        String systemToken = integrationCredentialsService.getCredential("meta_app", "system_user_token");
        return systemToken != null ? systemToken : "";
    }

    /** Enforces WhatsApp's component multiplicity (exactly one BODY, at most one HEADER/FOOTER/BUTTONS); returns an error message or null. */
    private String assertComponentMultiplicity(List<Map<String, Object>> components) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> c : components) {
            String type = str(c.get("type"));
            counts.merge(type, 1, Integer::sum);
        }
        if (counts.getOrDefault("BODY", 0) != 1) {
            return "Exactly one BODY component is required.";
        }
        for (String single : List.of("HEADER", "FOOTER", "BUTTONS")) {
            if (counts.getOrDefault(single, 0) > 1) {
                return "Only one " + single + " component is allowed.";
            }
        }
        return null;
    }

    /** Transforms components into the exact shape Meta's API expects, stripping null/empty fields, ported from buildMetaPayload(). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMetaPayload(String name, String language, String category, List<Map<String, Object>> components) {
        List<Map<String, Object>> built = new ArrayList<>();

        for (Map<String, Object> comp : components) {
            String type = str(comp.get("type"));

            if ("BUTTONS".equals(type)) {
                List<Map<String, Object>> buttons = new ArrayList<>();
                Object rawButtons = comp.get("buttons");
                if (rawButtons instanceof List) {
                    for (Object bObj : (List<Object>) rawButtons) {
                        Map<String, Object> btn = (Map<String, Object>) bObj;
                        Map<String, Object> b = new LinkedHashMap<>();
                        b.put("type", btn.get("type"));
                        b.put("text", btn.get("text"));
                        if ("URL".equals(btn.get("type"))) {
                            b.put("url", btn.getOrDefault("url", ""));
                            if (btn.get("example") != null) b.put("example", btn.get("example"));
                        } else if ("PHONE_NUMBER".equals(btn.get("type"))) {
                            b.put("phone_number", btn.getOrDefault("phone_number", ""));
                        }
                        buttons.add(b);
                    }
                }
                if (!buttons.isEmpty()) {
                    built.add(Map.of("type", "BUTTONS", "buttons", buttons));
                }
                continue;
            }

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("type", type);

            Map<String, Object> example = comp.get("example") instanceof Map ? (Map<String, Object>) comp.get("example") : Map.of();

            if ("HEADER".equals(type)) {
                String format = str(comp.getOrDefault("format", "TEXT"));
                b.put("format", format);
                if ("TEXT".equals(format)) {
                    b.put("text", comp.getOrDefault("text", ""));
                    Object headerText = example.get("header_text");
                    if (headerText instanceof List && !((List<?>) headerText).isEmpty()) {
                        b.put("example", Map.of("header_text", headerText));
                    }
                } else {
                    Object handles = example.get("header_handle");
                    if (handles instanceof List && !((List<?>) handles).isEmpty()) {
                        b.put("example", Map.of("header_handle", handles));
                    }
                }
            } else if ("BODY".equals(type)) {
                b.put("text", comp.getOrDefault("text", ""));
                Object bodyText = example.get("body_text");
                if (bodyText instanceof List && !((List<?>) bodyText).isEmpty()) {
                    b.put("example", Map.of("body_text", bodyText));
                }
            } else if ("FOOTER".equals(type)) {
                b.put("text", comp.getOrDefault("text", ""));
            }

            built.add(b);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("language", language);
        payload.put("category", category);
        payload.put("components", built);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private String metaErrorMessage(Map<String, Object> resp) {
        Object error = resp.get("error");
        if (error instanceof Map) {
            Map<String, Object> err = (Map<String, Object>) error;
            String userMsg = str(err.get("error_user_msg"));
            if (userMsg != null && !userMsg.isBlank()) return userMsg;
            String msg = str(err.get("message"));
            if (msg != null && !msg.isBlank()) return msg;
        }
        return "Meta rejected the template (HTTP " + resp.get("_status") + ")";
    }

    private Map<String, Object> templateSummary(WhatsappTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("language", t.getLanguage());
        m.put("category", t.getCategory());
        m.put("status", t.getStatus());
        m.put("waba_id", t.getWabaId());
        m.put("rejection_reason", t.getRejectionReason());
        m.put("components", parseComponents(t.getComponents()));
        m.put("created_at", t.getCreatedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> componentsFrom(Object raw) {
        if (raw instanceof List) {
            return (List<Map<String, Object>>) (List<?>) raw;
        }
        return List.of();
    }

    private Object parseComponents(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
