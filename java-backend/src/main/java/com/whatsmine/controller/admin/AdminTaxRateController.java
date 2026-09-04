package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.TaxRate;
import com.whatsmine.repository.TaxRateRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/tax-rates")
public class AdminTaxRateController {

    private final TaxRateRepository taxRateRepository;

    public AdminTaxRateController(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    @GetMapping
    public Object index() {
        List<TaxRate> rates = taxRateRepository.findAll();
        return Inertia.render("Admin/TaxRates/Index", Map.of("taxRates", rates));
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        TaxRate taxRate = new TaxRate();
        taxRate.setName((String) payload.get("name"));
        taxRate.setCountry((String) payload.get("country"));
        taxRate.setRegion((String) payload.get("region"));
        taxRate.setPercentage(payload.get("percentage") != null ? new BigDecimal(payload.get("percentage").toString()) : BigDecimal.ZERO);
        taxRate.setInclusive(Boolean.TRUE.equals(payload.get("inclusive")));
        taxRate.setEnabled(payload.get("enabled") == null || Boolean.TRUE.equals(payload.get("enabled")));

        taxRateRepository.save(taxRate);

        Inertia.flashSuccess(session, "Tax rate created.");
        return Inertia.redirect("/admin/tax-rates");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpSession session) {
        TaxRate taxRate = taxRateRepository.findById(id).orElse(null);
        if (taxRate == null) {
            return Inertia.redirect("/admin/tax-rates");
        }

        if (payload.get("name") != null) {
            taxRate.setName((String) payload.get("name"));
        }
        if (payload.get("country") != null) {
            taxRate.setCountry((String) payload.get("country"));
        }
        if (payload.containsKey("region")) {
            taxRate.setRegion((String) payload.get("region"));
        }
        if (payload.get("percentage") != null) {
            taxRate.setPercentage(new BigDecimal(payload.get("percentage").toString()));
        }
        if (payload.containsKey("inclusive")) {
            taxRate.setInclusive(Boolean.TRUE.equals(payload.get("inclusive")));
        }
        if (payload.containsKey("enabled")) {
            taxRate.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));
        }

        taxRateRepository.save(taxRate);

        Inertia.flashSuccess(session, "Tax rate updated.");
        return Inertia.redirect("/admin/tax-rates");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        taxRateRepository.deleteById(id);
        Inertia.flashSuccess(session, "Tax rate deleted.");
        return Inertia.redirect("/admin/tax-rates");
    }
}
