package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.service.billing.BillingGatewayInterface;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AdminTransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BillingGatewayRegistry gatewayRegistry;

    public AdminTransactionController(PaymentTransactionRepository paymentTransactionRepository,
                                      BillingGatewayRegistry gatewayRegistry) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.gatewayRegistry = gatewayRegistry;
    }

    @GetMapping("/admin/transactions")
    public Object index(@RequestParam(value = "page", defaultValue = "1") int page) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> pageResult = paymentTransactionRepository.findAll(pageRequest);

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", pageResult.getContent());
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        return Inertia.render("Admin/Transactions/Index", Map.of("transactions", paginated));
    }

    @PostMapping("/admin/payments/{id}/refund")
    public Object refund(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload, HttpSession session) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(id).orElse(null);
        if (transaction == null) {
            Inertia.flashError(session, "Transaction not found.");
            return Inertia.redirect("/admin/payments");
        }

        if (transaction.getRefundedAt() != null) {
            Inertia.flashError(session, "Transaction has already been refunded.");
            return Inertia.redirect("/admin/payments");
        }

        Integer amountCents = null;
        String reason = null;
        if (payload != null) {
            if (payload.get("amount_cents") != null) {
                amountCents = Integer.valueOf(payload.get("amount_cents").toString());
            }
            if (payload.get("reason") != null) {
                reason = (String) payload.get("reason");
            }
        }

        BillingGatewayInterface gateway = gatewayRegistry.get(transaction.getGateway() != null ? transaction.getGateway() : "stripe");
        if (gateway == null) {
            Inertia.flashError(session, "Gateway not configured.");
            return Inertia.redirect("/admin/payments");
        }

        Map<String, Object> result = gateway.refund(transaction, amountCents);
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            Inertia.flashError(session, (String) result.getOrDefault("error", "Refund failed."));
            return Inertia.redirect("/admin/payments");
        }

        if (reason != null && !reason.isBlank()) {
            transaction.setRefundReason(reason);
            paymentTransactionRepository.save(transaction);
        }

        Inertia.flashSuccess(session, "Refund processed.");
        return Inertia.redirect("/admin/payments");
    }
}
