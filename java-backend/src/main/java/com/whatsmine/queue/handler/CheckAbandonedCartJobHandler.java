package com.whatsmine.queue.handler;

import com.whatsmine.model.EcommerceCart;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.EcommerceCartRepository;
import com.whatsmine.repository.EcommerceOrderRepository;
import com.whatsmine.service.automation.AutomationTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ports php/app/Modules/Ecommerce/Jobs/CheckAbandonedCartJob.php: fires the
 * cart.abandoned automation trigger if a checkout was not converted into an
 * order within the delay window scheduled by EcommerceWebhookProcessor.
 * Idempotent — guarded by recovered_at / recovery_triggered_at.
 */
@Component
public class CheckAbandonedCartJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckAbandonedCartJobHandler.class);

    private final EcommerceCartRepository cartRepository;
    private final EcommerceOrderRepository orderRepository;
    private final AutomationTriggerService automationTriggerService;

    public CheckAbandonedCartJobHandler(EcommerceCartRepository cartRepository,
                                         EcommerceOrderRepository orderRepository,
                                         AutomationTriggerService automationTriggerService) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.automationTriggerService = automationTriggerService;
    }

    @Override
    public String getJobType() {
        return "CheckAbandonedCartJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("cartId") == null) return;
        Long cartId = ((Number) data.get("cartId")).longValue();

        EcommerceCart cart = cartRepository.findById(cartId).orElse(null);
        if (cart == null || cart.getRecoveredAt() != null || cart.getRecoveryTriggeredAt() != null || cart.getContactId() == null) {
            return;
        }

        // If an order was placed by this contact after the cart was created, it converted.
        LocalDateTime since = cart.getAbandonedAt() != null ? cart.getAbandonedAt() : cart.getCreatedAt();
        boolean converted = orderRepository.existsByStoreIdAndContactIdAndPlacedAtGreaterThanEqual(
                cart.getStoreId(), cart.getContactId(), since != null ? since : LocalDateTime.now());

        if (converted) {
            cart.setRecoveredAt(LocalDateTime.now());
            cartRepository.save(cart);
            return;
        }

        cart.setRecoveryTriggeredAt(LocalDateTime.now());
        cartRepository.save(cart);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("cart_total", String.valueOf(cart.getTotal()));
        context.put("order_currency", cart.getCurrency());
        context.put("recovery_url", cart.getRecoveryUrl());

        try {
            automationTriggerService.fireForContact(cart.getWorkspaceId(), "cart.abandoned", cart.getContactId(), context);
        } catch (Exception e) {
            log.warn("CheckAbandonedCartJob: cart.abandoned trigger failed for cart {} contact {}: {}", cart.getId(), cart.getContactId(), e.getMessage());
        }
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
