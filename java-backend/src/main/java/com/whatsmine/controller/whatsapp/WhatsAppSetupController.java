package com.whatsmine.controller.whatsapp;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappPhoneNumber;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappPhoneNumberRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/app/whatsapp/setup")
public class WhatsAppSetupController {

    private final WhatsappBusinessAccountRepository wabaRepository;
    private final WhatsappPhoneNumberRepository phoneNumberRepository;

    public WhatsAppSetupController(
            WhatsappBusinessAccountRepository wabaRepository,
            WhatsappPhoneNumberRepository phoneNumberRepository) {
        this.wabaRepository = wabaRepository;
        this.phoneNumberRepository = phoneNumberRepository;
    }

    @PostMapping("/embedded-signup")
    @Transactional
    public Object embeddedSignup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody EmbeddedSignupRequest request,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();

        Optional<WhatsappBusinessAccount> existingOpt = wabaRepository.findByWorkspaceIdAndWabaId(workspaceId, request.getWabaId());

        WhatsappBusinessAccount waba = existingOpt.orElseGet(WhatsappBusinessAccount::new);
        waba.setWorkspaceId(workspaceId);
        waba.setWabaId(request.getWabaId());
        waba.setWebhookVerifyToken(request.getWebhookVerifyToken() != null ? request.getWebhookVerifyToken() : "waba_token_" + System.currentTimeMillis());
        waba.setStatus("active");
        waba = wabaRepository.save(waba);

        if (request.getPhoneNumberId() != null) {
            WhatsappPhoneNumber phone = new WhatsappPhoneNumber();
            phone.setWabaIdFk(waba.getId());
            phone.setPhoneNumberId(request.getPhoneNumberId());
            phone.setDisplayPhone(request.getDisplayPhone() != null ? request.getDisplayPhone() : "+1234567890");
            phone.setQualityRating("GREEN");
            phoneNumberRepository.save(phone);
        }

        Inertia.flashSuccess(session, "WhatsApp Business Account connected successfully.");
        return Inertia.redirect("/app/dashboard");
    }

    @DeleteMapping("/{wabaId}")
    @Transactional
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String wabaId,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();
        Optional<WhatsappBusinessAccount> wabaOpt = wabaRepository.findByWabaId(wabaId);

        if (wabaOpt.isPresent()) {
            WhatsappBusinessAccount waba = wabaOpt.get();
            if (!waba.getWorkspaceId().equals(workspaceId)) {
                throw new AccessDeniedException("Access denied to requested WhatsApp account.");
            }
            wabaRepository.delete(waba);
        }

        Inertia.flashSuccess(session, "WhatsApp Business Account disconnected.");
        return Inertia.redirect("/app/dashboard");
    }

    public static class EmbeddedSignupRequest {
        @NotBlank
        private String wabaId;
        private String phoneNumberId;
        private String displayPhone;
        private String webhookVerifyToken;

        public String getWabaId() { return wabaId; }
        public void setWabaId(String wabaId) { this.wabaId = wabaId; }
        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getDisplayPhone() { return displayPhone; }
        public void setDisplayPhone(String displayPhone) { this.displayPhone = displayPhone; }
        public String getWebhookVerifyToken() { return webhookVerifyToken; }
        public void setWebhookVerifyToken(String webhookVerifyToken) { this.webhookVerifyToken = webhookVerifyToken; }
    }
}
