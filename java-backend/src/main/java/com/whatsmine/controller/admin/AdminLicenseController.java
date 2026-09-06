package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.service.license.LicenseManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin "License &amp; Updates" page, porting PHP's
 * Admin\LicenseController. See LicenseManager for what's genuinely ported
 * vs. the honest apply-update limitation.
 */
@RestController
@RequestMapping("/admin/license")
public class AdminLicenseController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private LicenseManager licenseManager;

    @GetMapping
    public Object index(HttpServletRequest request) {
        Map<String, Object> license = new LinkedHashMap<>();
        license.put("enabled", licenseManager.enabled());
        license.put("activated", licenseManager.isActivated());
        license.put("verify_type", licenseManager.activatedType());
        license.put("verify_types", licenseManager.verifyTypes());
        license.put("masked_code", licenseManager.maskedCode());
        license.put("product_id", licenseManager.productId());
        license.put("current_version", licenseManager.currentVersion());

        return inertiaRenderer.render("Admin/License/Index", Map.of("license", license), request);
    }

    @PostMapping("/check-update")
    public ResponseEntity<Map<String, Object>> checkUpdate() {
        return ResponseEntity.ok(licenseManager.checkUpdate());
    }

    @PostMapping("/apply-update")
    public ResponseEntity<Map<String, Object>> applyUpdate() {
        return ResponseEntity.ok(licenseManager.applyUpdateUnsupported());
    }

    @PostMapping("/activate")
    public Object activate(@RequestBody Map<String, Object> body, HttpSession session) {
        String licenseCode = body != null ? str(body.get("license_code")) : null;
        String clientName = body != null ? str(body.get("client_name")) : null;
        String verifyType = body != null ? str(body.get("verify_type")) : null;

        Map<String, Object> result = licenseManager.activate(licenseCode, clientName, verifyType);
        if (Boolean.TRUE.equals(result.get("ok"))) {
            Inertia.flashSuccess(session, String.valueOf(result.get("message")));
        } else {
            Inertia.flashError(session, String.valueOf(result.get("message")));
        }
        return Inertia.redirect("/admin/license");
    }

    @PostMapping("/deactivate")
    public Object deactivate(HttpSession session) {
        Map<String, Object> result = licenseManager.deactivate();
        if (Boolean.TRUE.equals(result.get("ok"))) {
            Inertia.flashSuccess(session, String.valueOf(result.get("message")));
        } else {
            Inertia.flashError(session, String.valueOf(result.get("message")));
        }
        return Inertia.redirect("/admin/license");
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
