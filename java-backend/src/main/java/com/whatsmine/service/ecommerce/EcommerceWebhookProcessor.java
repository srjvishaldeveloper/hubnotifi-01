package com.whatsmine.service.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Contact;
import com.whatsmine.model.EcommerceCart;
import com.whatsmine.model.EcommerceOrder;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.EcommerceCartRepository;
import com.whatsmine.repository.EcommerceOrderRepository;
import com.whatsmine.service.automation.AutomationTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a verified e-commerce order webhook into a real EcommerceOrder row,
 * a resolved/created Contact, and a fired automation trigger — porting the
 * order-handling path of php/app/Modules/Ecommerce/Jobs/ProcessEcommerceWebhookJob.php.
 * Runs synchronously inside the webhook request (like this app's Stripe/
 * Razorpay webhook handlers) rather than through the job queue, since the
 * queue's own job handlers are unwired dead code elsewhere in this port.
 * Cart/checkout ("cart.abandoned") events are the separate, still-unported
 * abandoned-cart-reminder feature — out of scope here.
 */
@Service
public class EcommerceWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(EcommerceWebhookProcessor.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final EcommerceOrderNormalizer normalizer;
    private final ContactRepository contactRepository;
    private final EcommerceOrderRepository orderRepository;
    private final EcommerceCartRepository cartRepository;
    private final AutomationTriggerService automationTriggerService;
    private final ObjectMapper objectMapper;

    public EcommerceWebhookProcessor(EcommerceOrderNormalizer normalizer,
                                      ContactRepository contactRepository,
                                      EcommerceOrderRepository orderRepository,
                                      EcommerceCartRepository cartRepository,
                                      AutomationTriggerService automationTriggerService,
                                      ObjectMapper objectMapper) {
        this.normalizer = normalizer;
        this.contactRepository = contactRepository;
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.automationTriggerService = automationTriggerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(EcommerceStore store, String platform, String topic, Map<String, Object> payload) {
        EcommerceOrderNormalizer.NormalizedOrder event = switch (platform) {
            case "shopify" -> normalizer.shopify(topic, payload);
            case "woocommerce" -> normalizer.woocommerce(topic, payload);
            case "bigcommerce" -> hydrateAndNormalizeBigCommerce(store, topic, payload);
            default -> null;
        };

        if (event == null || event.order == null) {
            return;
        }

        Contact contact = resolveContact(store.getWorkspaceId(), event.contact);
        handleOrder(store, contact, event);
    }

    // ── BigCommerce hydration ──────────────────────────────────────────
    // BigCommerce webhooks are a lightweight {scope, data:{id}} reference;
    // fetch the real order (+ line items, a separate v2 endpoint) before
    // normalizing, matching BigCommerceClient::hydrateWebhook/fetchOrder.

    @SuppressWarnings("unchecked")
    private EcommerceOrderNormalizer.NormalizedOrder hydrateAndNormalizeBigCommerce(EcommerceStore store, String scope, Map<String, Object> payload) {
        Object dataObj = payload.get("data");
        Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : Map.of();
        String orderId = data.get("id") != null ? String.valueOf(data.get("id")) : null;
        if (orderId == null) {
            return null;
        }

        String forcedEvent = "store/order/created".equals(scope) ? "order.placed" : null;
        if (!"store/order/created".equals(scope) && !"store/order/statusUpdated".equals(scope)) {
            return null;
        }

        Map<String, Object> order = fetchBigCommerceOrder(store, orderId);
        if (order == null) {
            return null;
        }

        String eventType = forcedEvent != null ? forcedEvent : eventFromBigCommerceStatus(String.valueOf(order.get("status")));
        if (eventType == null) {
            eventType = "order.updated";
        }

        return normalizer.bigcommerce(eventType, order);
    }

    private String eventFromBigCommerceStatus(String status) {
        if (List.of("Shipped", "Completed", "Partially Shipped").contains(status)) return "order.fulfilled";
        if (List.of("Cancelled", "Refunded", "Declined").contains(status)) return "order.cancelled";
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchBigCommerceOrder(EcommerceStore store, String orderId) {
        String storeHash = store.getDomain();
        String accessToken = store.getCredentials() != null ? String.valueOf(store.getCredentials().getOrDefault("access_token", "")) : "";
        if (accessToken.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> order = getBigCommerceJson(storeHash, accessToken, "/v2/orders/" + orderId, Map.class);
            if (order == null) {
                return null;
            }
            Object products = getBigCommerceJson(storeHash, accessToken, "/v2/orders/" + orderId + "/products", List.class);
            order.put("_products", products != null ? products : List.of());
            return order;
        } catch (Exception e) {
            log.warn("BigCommerce order hydration failed for store {} order {}: {}", store.getId(), orderId, e.getMessage());
            return null;
        }
    }

    private <T> T getBigCommerceJson(String storeHash, String accessToken, String path, Class<T> type) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bigcommerce.com/stores/" + storeHash + path))
                .timeout(Duration.ofSeconds(15))
                .header("X-Auth-Token", accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
            return null;
        }
        return objectMapper.readValue(response.body(), type);
    }

    // ── Contact + order upsert ─────────────────────────────────────────

    private Contact resolveContact(Long workspaceId, Map<String, String> contactData) {
        String email = contactData.get("email");
        String phone = contactData.get("phone_e164");
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            return null;
        }

        Contact contact = null;
        if (phone != null && !phone.isBlank()) {
            contact = contactRepository.findByWorkspaceIdAndPhoneE164(workspaceId, phone).orElse(null);
        }
        if (contact == null && email != null && !email.isBlank()) {
            contact = contactRepository.findByWorkspaceIdAndEmail(workspaceId, email).orElse(null);
        }

        if (contact == null) {
            contact = new Contact();
            contact.setWorkspaceId(workspaceId);
            contact.setSource("ecommerce");
        }
        if (phone != null && !phone.isBlank()) contact.setPhoneE164(phone);
        if (email != null && !email.isBlank()) contact.setEmail(email);
        if (contactData.get("first_name") != null && !contactData.get("first_name").isBlank()) contact.setFirstName(contactData.get("first_name"));
        if (contactData.get("last_name") != null && !contactData.get("last_name").isBlank()) contact.setLastName(contactData.get("last_name"));

        return contactRepository.save(contact);
    }

    @SuppressWarnings("unchecked")
    private void handleOrder(EcommerceStore store, Contact contact, EcommerceOrderNormalizer.NormalizedOrder event) {
        String externalOrderId = String.valueOf(event.order.get("external_order_id"));

        EcommerceOrder order = orderRepository.findByStoreIdAndExternalOrderId(store.getId(), externalOrderId).orElseGet(EcommerceOrder::new);
        order.setStoreId(store.getId());
        order.setWorkspaceId(store.getWorkspaceId());
        order.setExternalOrderId(externalOrderId);
        if (contact != null) {
            order.setContactId(contact.getId());
        }

        // Only overwrite fields the source actually provided, so a status-only
        // update doesn't wipe tracking/fulfillment info from an earlier event.
        setIfPresent(event.order, "platform", order::setPlatform);
        setIfPresent(event.order, "number", order::setNumber);
        setIfPresent(event.order, "status", order::setStatus);
        setIfPresent(event.order, "financial_status", order::setFinancialStatus);
        setIfPresent(event.order, "fulfillment_status", order::setFulfillmentStatus);
        setIfPresent(event.order, "currency", order::setCurrency);
        if (event.order.get("total") instanceof java.math.BigDecimal total) {
            order.setTotal(total);
        }
        Object lineItems = event.order.get("line_items");
        if (lineItems instanceof List) {
            order.setLineItems((List<Map<String, Object>>) lineItems);
        }
        setIfPresent(event.order, "tracking_url", order::setTrackingUrl);
        setIfPresent(event.order, "tracking_number", order::setTrackingNumber);
        if (event.order.get("placed_at") instanceof LocalDateTime placedAt) {
            order.setPlacedAt(placedAt);
        }
        Object raw = event.order.get("raw");
        if (raw instanceof Map) {
            order.setRaw((Map<String, Object>) raw);
        }

        orderRepository.save(order);

        // An order arriving for a checkout means the cart was recovered.
        if (contact != null) {
            List<EcommerceCart> openCarts = cartRepository.findByStoreIdAndContactIdAndRecoveredAtIsNull(store.getId(), contact.getId());
            for (EcommerceCart cart : openCarts) {
                cart.setRecoveredAt(LocalDateTime.now());
                cartRepository.save(cart);
            }
        }

        if (contact == null) {
            return;
        }

        try {
            Map<String, Object> context = new LinkedHashMap<>(event.context);
            automationTriggerService.fireForContact(store.getWorkspaceId(), event.eventType, contact.getId(), context);
        } catch (Exception e) {
            log.warn("{} automation trigger failed for contact {}: {}", event.eventType, contact.getId(), e.getMessage());
        }
    }

    private void setIfPresent(Map<String, Object> order, String key, java.util.function.Consumer<String> setter) {
        Object v = order.get(key);
        if (v != null) {
            setter.accept(String.valueOf(v));
        }
    }
}
