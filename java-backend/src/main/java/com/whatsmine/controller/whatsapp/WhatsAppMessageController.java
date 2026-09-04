package com.whatsmine.controller.whatsapp;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/app/whatsapp/messages")
public class WhatsAppMessageController {

    private final WhatsAppApiClient apiClient;
    private final WhatsappBusinessAccountRepository wabaRepository;

    public WhatsAppMessageController(WhatsAppApiClient apiClient, WhatsappBusinessAccountRepository wabaRepository) {
        this.apiClient = apiClient;
        this.wabaRepository = wabaRepository;
    }

    @PostMapping("/send")
    public Object sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SendMessageRequest request,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();

        Optional<WhatsappBusinessAccount> waba = wabaRepository.findFirstByWorkspaceIdAndStatus(workspaceId, "active");
        if (waba.isEmpty()) {
            throw new AccessDeniedException("No active WhatsApp Business Account found for workspace.");
        }

        Map<String, Object> apiResponse;
        if ("template".equalsIgnoreCase(request.getType())) {
            apiResponse = apiClient.sendTemplateMessage(
                    "phone_num_id",
                    "access_token",
                    request.getTo(),
                    request.getTemplateName(),
                    request.getLanguage(),
                    null
            );
        } else {
            apiResponse = apiClient.sendTextMessage(
                    "phone_num_id",
                    "access_token",
                    request.getTo(),
                    request.getText()
            );
        }

        Inertia.flashSuccess(session, "WhatsApp message sent successfully.");
        return Inertia.redirect("/app/dashboard");
    }

    public static class SendMessageRequest {
        @NotBlank
        private String to;
        private String type = "text";
        private String text;
        private String templateName;
        private String language;

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}
