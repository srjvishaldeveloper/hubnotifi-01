package com.whatsmine.service.ecommerce;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes raw Shopify / WooCommerce / (hydrated) BigCommerce order
 * webhook payloads into one canonical shape, porting the order-handling
 * paths of php/app/Modules/Ecommerce/Services/PayloadNormalizer.php.
 * Cart/checkout ("cart.abandoned") and customer/product events are a
 * separate, still-unported feature (abandoned-cart reminders) — out of
 * scope here, so those topics are simply ignored (return null).
 */
@Component
public class EcommerceOrderNormalizer {

    public static class NormalizedOrder {
        public String eventType;
        public Map<String, Object> order;
        public Map<String, String> contact;
        public Map<String, String> context;
    }

    @SuppressWarnings("unchecked")
    public NormalizedOrder shopify(String topic, Map<String, Object> p) {
        String eventType = switch (topic) {
            case "orders/create" -> "order.placed";
            case "orders/fulfilled" -> "order.fulfilled";
            case "orders/cancelled" -> "order.cancelled";
            default -> null;
        };
        if (eventType == null) return null;

        Map<String, Object> customer = mapOf(p.get("customer"));
        Map<String, Object> ship = mapOf(p.get("shipping_address"));
        Map<String, Object> bill = mapOf(p.get("billing_address"));
        List<Object> fulfillments = listOf(p.get("fulfillments"));
        Map<String, Object> fulfillment = fulfillments.isEmpty() ? Map.of() : mapOf(fulfillments.get(0));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("external_order_id", str(p.get("id")));
        order.put("platform", "shopify");
        order.put("number", p.get("name") != null ? str(p.get("name")) : "#" + str(p.get("order_number")));
        order.put("status", p.get("cancelled_at") != null ? "cancelled" : "open");
        order.put("financial_status", p.get("financial_status"));
        order.put("fulfillment_status", p.get("fulfillment_status"));
        order.put("currency", p.get("currency"));
        order.put("total", toBigDecimal(p.get("total_price")));
        order.put("line_items", lineItems(listOf(p.get("line_items")), "title"));
        order.put("tracking_url", fulfillment.get("tracking_url") != null ? fulfillment.get("tracking_url") : firstOf(fulfillment.get("tracking_urls")));
        order.put("tracking_number", fulfillment.get("tracking_number"));
        order.put("placed_at", parseDate(str(p.get("created_at"))));
        order.put("raw", p);

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("phone_e164", phone(firstNonBlank(str(p.get("phone")), str(customer.get("phone")), str(ship.get("phone")), str(bill.get("phone")))));
        contact.put("email", firstNonBlank(str(p.get("email")), str(customer.get("email"))));
        contact.put("first_name", firstNonBlank(str(customer.get("first_name")), str(ship.get("first_name"))));
        contact.put("last_name", firstNonBlank(str(customer.get("last_name")), str(ship.get("last_name"))));

        NormalizedOrder result = new NormalizedOrder();
        result.eventType = eventType;
        result.order = order;
        result.contact = contact;
        result.context = orderContext(order);
        return result;
    }

    @SuppressWarnings("unchecked")
    public NormalizedOrder woocommerce(String topic, Map<String, Object> p) {
        String status = str(p.get("status"));
        String eventType;
        if ("order.created".equals(topic)) {
            eventType = "order.placed";
        } else if ("order.updated".equals(topic) && "completed".equals(status)) {
            eventType = "order.fulfilled";
        } else if ("order.updated".equals(topic) && ("cancelled".equals(status) || "refunded".equals(status))) {
            eventType = "order.cancelled";
        } else {
            return null;
        }

        Map<String, Object> billing = mapOf(p.get("billing"));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("external_order_id", str(p.get("id")));
        order.put("platform", "woocommerce");
        order.put("number", p.get("number") != null ? str(p.get("number")) : str(p.get("id")));
        order.put("status", status);
        order.put("financial_status", status);
        order.put("fulfillment_status", "completed".equals(status) ? "fulfilled" : null);
        order.put("currency", p.get("currency"));
        order.put("total", toBigDecimal(p.get("total")));
        order.put("line_items", lineItems(listOf(p.get("line_items")), "name"));
        order.put("tracking_url", null);
        order.put("tracking_number", null);
        order.put("placed_at", parseDate(firstNonBlank(str(p.get("date_created")), str(p.get("date_created_gmt")))));
        order.put("raw", p);

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("phone_e164", phone(str(billing.get("phone"))));
        contact.put("email", str(billing.get("email")));
        contact.put("first_name", str(billing.get("first_name")));
        contact.put("last_name", str(billing.get("last_name")));

        NormalizedOrder result = new NormalizedOrder();
        result.eventType = eventType;
        result.order = order;
        result.contact = contact;
        result.context = orderContext(order);
        return result;
    }

    /** eventType is already resolved by the caller's hydration step (see EcommerceWebhookProcessor). */
    @SuppressWarnings("unchecked")
    public NormalizedOrder bigcommerce(String eventType, Map<String, Object> p) {
        Map<String, Object> bill = mapOf(p.get("billing_address"));
        String status = str(p.get("status"));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("external_order_id", str(p.get("id")));
        order.put("platform", "bigcommerce");
        order.put("number", "#" + str(p.get("id")));
        order.put("status", status);
        order.put("financial_status", p.get("payment_status"));
        order.put("fulfillment_status", List.of("Shipped", "Completed", "Partially Shipped").contains(status) ? "fulfilled" : null);
        order.put("currency", p.get("currency_code"));
        order.put("total", toBigDecimal(firstNonNull(p.get("total_inc_tax"), 0)));

        List<Object> products = listOf(p.get("_products"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object o : products) {
            Map<String, Object> i = mapOf(o);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", str(i.get("name")));
            item.put("quantity", toInt(i.get("quantity"), 1));
            item.put("price", str(firstNonNull(i.get("price_inc_tax"), i.get("base_price"), "0")));
            items.add(item);
        }
        order.put("line_items", items);
        order.put("tracking_url", null);
        order.put("tracking_number", null);
        order.put("placed_at", parseDate(str(p.get("date_created"))));
        order.put("raw", p);

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("phone_e164", phone(str(bill.get("phone"))));
        contact.put("email", str(bill.get("email")));
        contact.put("first_name", str(bill.get("first_name")));
        contact.put("last_name", str(bill.get("last_name")));

        NormalizedOrder result = new NormalizedOrder();
        result.eventType = eventType;
        result.order = order;
        result.contact = contact;
        result.context = orderContext(order);
        return result;
    }

    private Map<String, String> orderContext(Map<String, Object> order) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("order_number", str(order.get("number")));
        context.put("order_total", String.valueOf(order.get("total")));
        context.put("order_currency", str(order.get("currency")));
        context.put("order_status", str(order.get("status")));
        context.put("tracking_url", str(order.get("tracking_url")));
        context.put("tracking_number", str(order.get("tracking_number")));
        return context;
    }

    private List<Map<String, Object>> lineItems(List<Object> items, String titleKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : items) {
            Map<String, Object> i = mapOf(o);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", firstNonBlank(str(i.get(titleKey)), str(i.get("name"))));
            item.put("quantity", toInt(i.get("quantity"), 1));
            item.put("price", str(firstNonNull(i.get("price"), "0")));
            result.add(item);
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listOf(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    private Object firstOf(Object listObj) {
        List<Object> list = listOf(listObj);
        return list.isEmpty() ? null : list.get(0);
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int toInt(Object o, int fallback) {
        if (o == null) return fallback;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a platform date string; empty/unparseable input becomes null
     * rather than "now" (a bad placed_at would otherwise silently corrupt
     * order history and any future abandoned-cart timing logic).
     */
    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) { }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) { }
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) { }
        return null;
    }

    /** Best-effort E.164 cleanup: keep a leading +, strip everything else non-digit. */
    private String phone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        boolean plus = raw.startsWith("+");
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) return null;
        return (plus ? "+" : "") + digits;
    }
}
