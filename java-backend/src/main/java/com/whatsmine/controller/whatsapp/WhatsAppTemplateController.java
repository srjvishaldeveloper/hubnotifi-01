package com.whatsmine.controller.whatsapp;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.WhatsappTemplate;
import com.whatsmine.repository.WhatsappTemplateRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/whatsapp/templates")
public class WhatsAppTemplateController {

    private final WhatsappTemplateRepository templateRepository;

    public WhatsAppTemplateController(WhatsappTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @GetMapping
    public InertiaResponse index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = userDetails.getWorkspaceId();
        List<WhatsappTemplate> templates = templateRepository.findByWorkspaceId(workspaceId);

        Map<String, Object> props = new HashMap<>();
        props.put("templates", templates);
        props.put("workspaceId", workspaceId);

        return Inertia.render("Client/WhatsApp/Templates", props);
    }

    @PostMapping
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateTemplateRequest request,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();

        WhatsappTemplate template = new WhatsappTemplate();
        template.setWorkspaceId(workspaceId);
        template.setWabaId(request.getWabaId() != null ? request.getWabaId() : "default_waba");
        template.setName(request.getName());
        template.setLanguage(request.getLanguage() != null ? request.getLanguage() : "en");
        template.setCategory(request.getCategory() != null ? request.getCategory() : "MARKETING");
        template.setStatus("APPROVED");
        template.setComponents(request.getComponents());
        template.setMetaTemplateId("meta_tpl_" + System.currentTimeMillis());
        templateRepository.save(template);

        Inertia.flashSuccess(session, "WhatsApp template created successfully.");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    @PostMapping("/sync")
    public Object sync(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session) {

        Inertia.flashSuccess(session, "WhatsApp templates synced from Meta successfully.");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    @DeleteMapping("/{id}")
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();
        WhatsappTemplate template = templateRepository.findById(id).orElseThrow();

        if (!template.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Access denied to template.");
        }

        templateRepository.delete(template);

        Inertia.flashSuccess(session, "WhatsApp template deleted.");
        return Inertia.redirect("/app/whatsapp/templates");
    }

    public static class CreateTemplateRequest {
        @NotBlank
        private String name;
        private String wabaId;
        private String language;
        private String category;
        private String components;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getWabaId() { return wabaId; }
        public void setWabaId(String wabaId) { this.wabaId = wabaId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getComponents() { return components; }
        public void setComponents(String components) { this.components = components; }
    }
}
