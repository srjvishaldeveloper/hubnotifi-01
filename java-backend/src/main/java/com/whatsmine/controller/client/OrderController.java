package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Contact;
import com.whatsmine.model.EcommerceOrder;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.EcommerceOrderRepository;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ecommerce/orders")
public class OrderController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private EcommerceOrderRepository orderRepository;

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private ContactRepository contactRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long store_id,
            @RequestParam(required = false) String fulfillment,
            @RequestParam(required = false) String financial,
            @RequestParam(required = false) String search
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<EcommerceOrder> allOrders = orderRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);

        List<EcommerceOrder> filtered = allOrders.stream().filter(o -> {
            if (store_id != null && !store_id.equals(o.getStoreId())) return false;
            if (fulfillment != null && !fulfillment.isBlank() && !fulfillment.equalsIgnoreCase(o.getFulfillmentStatus())) return false;
            if (financial != null && !financial.isBlank() && !financial.equalsIgnoreCase(o.getFinancialStatus())) return false;
            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase();
                boolean matchNum = o.getNumber() != null && o.getNumber().toLowerCase().contains(q);
                if (!matchNum) return false;
            }
            return true;
        }).toList();

        List<Map<String, Object>> orderData = filtered.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("number", o.getNumber());
            map.put("platform", o.getPlatform());
            map.put("status", o.getStatus());
            map.put("financial_status", o.getFinancialStatus());
            map.put("fulfillment_status", o.getFulfillmentStatus());
            map.put("currency", o.getCurrency());
            map.put("total", o.getTotal());
            map.put("placed_at", o.getPlacedAt());

            if (o.getContactId() != null) {
                contactRepository.findById(o.getContactId()).ifPresent(c -> {
                    map.put("contact", Map.of(
                            "uuid", c.getUuid() != null ? c.getUuid() : "",
                            "name", ((c.getFirstName() != null ? c.getFirstName() : "") + " " + (c.getLastName() != null ? c.getLastName() : "")).trim(),
                            "email", c.getEmail() != null ? c.getEmail() : ""
                    ));
                });
            }
            return map;
        }).toList();

        Map<String, Object> ordersPaginated = Map.of(
                "data", orderData,
                "total", orderData.size(),
                "current_page", 1,
                "last_page", 1
        );

        List<EcommerceStore> stores = storeRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        List<Map<String, Object>> storesList = stores.stream().map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName())).toList();

        double revenue = allOrders.stream().mapToDouble(o -> o.getTotal() != null ? o.getTotal().doubleValue() : 0.0).sum();
        long fulfilled = allOrders.stream().filter(o -> "fulfilled".equalsIgnoreCase(o.getFulfillmentStatus())).count();
        long unfulfilled = allOrders.size() - fulfilled;

        Map<String, Object> stats = Map.of(
                "total", allOrders.size(),
                "revenue", Math.round(revenue * 100.0) / 100.0,
                "fulfilled", fulfilled,
                "unfulfilled", unfulfilled
        );

        Map<String, Object> filters = new HashMap<>();
        filters.put("store_id", store_id);
        filters.put("fulfillment", fulfillment);
        filters.put("financial", financial);
        filters.put("search", search);

        Map<String, Object> props = Map.of(
                "orders", ordersPaginated,
                "filters", filters,
                "stores", storesList,
                "stats", stats
        );

        return inertiaRenderer.render("Ecommerce/Orders/Index", props, request);
    }

    @GetMapping("/{id}")
    public Object show(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceOrder order = orderRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", order.getId());
        orderMap.put("number", order.getNumber());
        orderMap.put("platform", order.getPlatform());
        orderMap.put("status", order.getStatus());
        orderMap.put("financial_status", order.getFinancialStatus());
        orderMap.put("fulfillment_status", order.getFulfillmentStatus());
        orderMap.put("currency", order.getCurrency());
        orderMap.put("total", order.getTotal());
        orderMap.put("line_items", order.getLineItems() != null ? order.getLineItems() : List.of());
        orderMap.put("tracking_url", order.getTrackingUrl());
        orderMap.put("tracking_number", order.getTrackingNumber());
        orderMap.put("placed_at", order.getPlacedAt());
        orderMap.put("external_order_id", order.getExternalOrderId());

        if (order.getStoreId() != null) {
            storeRepository.findById(order.getStoreId()).ifPresent(s -> {
                orderMap.put("store", Map.of("name", s.getName()));
            });
        }

        if (order.getContactId() != null) {
            contactRepository.findById(order.getContactId()).ifPresent(c -> {
                orderMap.put("contact", Map.of(
                        "uuid", c.getUuid() != null ? c.getUuid() : "",
                        "name", ((c.getFirstName() != null ? c.getFirstName() : "") + " " + (c.getLastName() != null ? c.getLastName() : "")).trim(),
                        "email", c.getEmail() != null ? c.getEmail() : "",
                        "phone", c.getPhoneE164() != null ? c.getPhoneE164() : ""
                ));
            });
        }

        return inertiaRenderer.render("Ecommerce/Orders/Show", Map.of("order", orderMap), request);
    }

    @PostMapping("/{id}/refresh")
    public Object refresh(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceOrder order = orderRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        Inertia.flashSuccess(session, "Order refreshed.");
        return Inertia.redirect("/app/ecommerce/orders/" + id);
    }

    @PostMapping("/{id}/fulfill")
    public Object fulfill(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceOrder order = orderRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if (body != null) {
            if (body.containsKey("tracking_number")) {
                order.setTrackingNumber(body.get("tracking_number"));
            }
            if (body.containsKey("tracking_url")) {
                order.setTrackingUrl(body.get("tracking_url"));
            }
        }
        order.setFulfillmentStatus("fulfilled");
        orderRepository.save(order);

        Inertia.flashSuccess(session, "Order marked as fulfilled.");
        return Inertia.redirect("/app/ecommerce/orders/" + id);
    }
}
