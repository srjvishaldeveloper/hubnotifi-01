package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.service.license.LicenseManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Standalone (guest) license re-activation page, porting PHP's
 * LicenseController (non-admin). The admin panel's license gate filter
 * redirects here when the copy's license is missing or invalid; once a
 * valid license is activated the operator is sent back to sign in.
 */
@RestController
@RequestMapping("/license")
public class LicenseController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private LicenseManager licenseManager;

    @GetMapping
    public Object show(HttpServletRequest request) {
        if (!licenseManager.enabled()) {
            return Inertia.redirect("/admin/login");
        }

        Map<String, Object> status = licenseManager.verify(true);
        if (Boolean.TRUE.equals(status.get("ok"))) {
            return Inertia.redirect("/admin/login");
        }

        Map<String, Object> props = Map.of(
                "reason", status.get("message"),
                "verify_type", licenseManager.defaultVerifyType(),
                "verify_types", licenseManager.verifyTypes()
        );
        return inertiaRenderer.render("License/Activate", props, request);
    }

    @PostMapping("/activate")
    public Object activate(@RequestBody Map<String, Object> body) {
        if (!licenseManager.enabled()) {
            return Inertia.redirect("/admin/login");
        }

        String licenseCode = body != null ? str(body.get("license_code")) : null;
        String clientName = body != null ? str(body.get("client_name")) : null;
        String verifyType = body != null ? str(body.get("verify_type")) : null;

        Map<String, Object> activation = licenseManager.activate(licenseCode, clientName, verifyType);
        if (!Boolean.TRUE.equals(activation.get("ok"))) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("errors", Map.of("license_code", activation.get("message"))));
        }

        Map<String, Object> verification = licenseManager.verify(false);
        if (!Boolean.TRUE.equals(verification.get("ok"))) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("errors", Map.of("license_code", verification.get("message"))));
        }

        return Inertia.redirect("/admin/login");
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
