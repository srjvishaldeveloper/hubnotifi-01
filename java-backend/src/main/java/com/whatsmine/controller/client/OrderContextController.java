package com.whatsmine.controller.client;

import com.whatsmine.model.Contact;
import com.whatsmine.model.EcommerceOrder;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.EcommerceOrderRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ecommerce/contacts")
public class OrderContextController {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private EcommerceOrderRepository orderRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/{contactId}/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrdersForContact(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contactId
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found."));

        if (!workspaceId.equals(contact.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        List<EcommerceOrder> orders = orderRepository.findByContactIdAndWorkspaceIdOrderByIdDesc(contactId, workspaceId);

        List<Map<String, Object>> result = orders.stream().limit(5).map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("number", o.getNumber());
            map.put("status", o.getStatus());
            map.put("financial_status", o.getFinancialStatus());
            map.put("fulfillment_status", o.getFulfillmentStatus());
            map.put("currency", o.getCurrency());
            map.put("total", o.getTotal());
            map.put("tracking_url", o.getTrackingUrl());
            map.put("placed_at", o.getPlacedAt());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
