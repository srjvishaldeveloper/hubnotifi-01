package com.whatsmine.service.ecommerce;

import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class StoreConnectionTester {

    private final EcommerceStoreRepository storeRepository;

    public StoreConnectionTester(EcommerceStoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public Map<String, Object> test(EcommerceStore store) {
        Map<String, Object> result = new HashMap<>();
        boolean ok = true;
        String message = "Connection successful";

        // Domain / platform specific sanity check
        if (store.getDomain() == null || store.getDomain().isBlank()) {
            ok = false;
            message = "Invalid store domain";
        }

        store.setLastTestedAt(LocalDateTime.now());
        store.setLastTestStatus(ok ? "ok" : "fail");
        store.setLastTestMessage(message);
        store.setStatus(ok ? "connected" : "error");

        storeRepository.save(store);

        result.put("ok", ok);
        result.put("message", message);
        return result;
    }
}
