package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Currency;
import com.whatsmine.repository.CurrencyRepository;
import jakarta.servlet.http.HttpSession;
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
@RequestMapping("/admin/currencies")
public class AdminCurrencyController {

    private final CurrencyRepository currencyRepository;

    public AdminCurrencyController(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @GetMapping
    public Object index() {
        List<Currency> currencies = currencyRepository.findAllByOrderByCodeAsc();
        return Inertia.render("Admin/Currencies/Index", Map.of("currencies", currencies));
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        String code = payload.get("code") != null ? payload.get("code").toString().trim().toUpperCase() : null;
        if (code == null || code.isEmpty() || currencyRepository.existsById(code)) {
            Inertia.flashError(session, "Currency code is required and must be unique.");
            return Inertia.redirect("/admin/currencies");
        }

        Currency currency = new Currency();
        currency.setCode(code);
        currency.setSymbol((String) payload.get("symbol"));
        currency.setDecimals(payload.get("decimals") != null ? Integer.parseInt(payload.get("decimals").toString()) : 2);
        currency.setExchangeRate(payload.get("exchange_rate") != null ? new BigDecimal(payload.get("exchange_rate").toString()) : BigDecimal.ONE);
        currency.setEnabled(payload.get("enabled") == null || Boolean.TRUE.equals(payload.get("enabled")));

        boolean isDefault = Boolean.TRUE.equals(payload.get("is_default"));
        if (isDefault) {
            currencyRepository.findAll().forEach(c -> { c.setDefault(false); currencyRepository.save(c); });
        }
        currency.setDefault(isDefault);

        currencyRepository.save(currency);

        Inertia.flashSuccess(session, "Currency added.");
        return Inertia.redirect("/admin/currencies");
    }

    @PutMapping("/{code}")
    public Object update(@PathVariable String code, @RequestBody Map<String, Object> payload, HttpSession session) {
        Currency currency = currencyRepository.findById(code).orElse(null);
        if (currency == null) {
            return Inertia.redirect("/admin/currencies");
        }

        if (payload.get("symbol") != null) {
            currency.setSymbol((String) payload.get("symbol"));
        }
        if (payload.get("decimals") != null) {
            currency.setDecimals(Integer.parseInt(payload.get("decimals").toString()));
        }
        if (payload.get("exchange_rate") != null) {
            currency.setExchangeRate(new BigDecimal(payload.get("exchange_rate").toString()));
        }
        if (payload.containsKey("enabled")) {
            currency.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));
        }
        if (Boolean.TRUE.equals(payload.get("is_default"))) {
            currencyRepository.findAll().forEach(c -> {
                if (!c.getCode().equals(code)) {
                    c.setDefault(false);
                    currencyRepository.save(c);
                }
            });
            currency.setDefault(true);
        }

        currencyRepository.save(currency);

        Inertia.flashSuccess(session, "Currency updated.");
        return Inertia.redirect("/admin/currencies");
    }
}
