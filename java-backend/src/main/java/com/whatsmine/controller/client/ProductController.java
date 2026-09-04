package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.EcommerceProduct;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceProductRepository;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ecommerce/products")
public class ProductController {

    public static final int LOW_STOCK_THRESHOLD = 5;

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private EcommerceProductRepository productRepository;

    @Autowired
    private EcommerceStoreRepository storeRepository;

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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long store_id,
            @RequestParam(required = false) Boolean low_stock
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<EcommerceProduct> allProducts = productRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);

        List<EcommerceProduct> filtered = allProducts.stream().filter(p -> {
            if (store_id != null && !store_id.equals(p.getStoreId())) {
                return false;
            }
            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase();
                boolean matchName = p.getName() != null && p.getName().toLowerCase().contains(q);
                boolean matchSku = p.getSku() != null && p.getSku().toLowerCase().contains(q);
                if (!matchName && !matchSku) return false;
            }
            if (Boolean.TRUE.equals(low_stock)) {
                if (p.getInventoryQuantity() == null || p.getInventoryQuantity() > LOW_STOCK_THRESHOLD) {
                    return false;
                }
            }
            return true;
        }).toList();

        List<Map<String, Object>> productData = filtered.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("sku", p.getSku());
            map.put("price", p.getPrice());
            map.put("inventory_quantity", p.getInventoryQuantity());
            map.put("status", p.getStatus());
            map.put("image_url", p.getImageUrl());
            map.put("platform", p.getPlatform());
            return map;
        }).toList();

        Map<String, Object> productsPaginated = Map.of(
                "data", productData,
                "total", productData.size(),
                "current_page", 1,
                "last_page", 1
        );

        List<EcommerceStore> stores = storeRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        List<Map<String, Object>> storesList = stores.stream().map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName())).toList();

        long lowStockCount = allProducts.stream().filter(p -> p.getInventoryQuantity() != null && p.getInventoryQuantity() <= LOW_STOCK_THRESHOLD).count();
        long outOfStockCount = allProducts.stream().filter(p -> p.getInventoryQuantity() != null && p.getInventoryQuantity() <= 0).count();

        Map<String, Object> stats = Map.of(
                "total", allProducts.size(),
                "low_stock", lowStockCount,
                "out_of_stock", outOfStockCount
        );

        Map<String, Object> filters = new HashMap<>();
        filters.put("store_id", store_id);
        filters.put("search", search);
        filters.put("low_stock", low_stock);

        Map<String, Object> props = Map.of(
                "products", productsPaginated,
                "filters", filters,
                "stores", storesList,
                "stats", stats,
                "lowStockThreshold", LOW_STOCK_THRESHOLD
        );

        return inertiaRenderer.render("Ecommerce/Products/Index", props, request);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "") String q
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<EcommerceProduct> products = productRepository.searchProductsList(workspaceId, q.isBlank() ? null : q.trim());

        List<Map<String, Object>> result = products.stream().limit(20).map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("sku", p.getSku());
            map.put("price", p.getPrice());
            map.put("inventory_quantity", p.getInventoryQuantity());
            map.put("status", p.getStatus());
            map.put("image_url", p.getImageUrl());
            map.put("platform", p.getPlatform());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
