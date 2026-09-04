package com.whatsmine.service.ecommerce;

import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StoreConnector {

    private final EcommerceStoreRepository storeRepository;
    private final StoreConnectionTester connectionTester;

    public StoreConnector(EcommerceStoreRepository storeRepository, StoreConnectionTester connectionTester) {
        this.storeRepository = storeRepository;
        this.connectionTester = connectionTester;
    }

    @Transactional
    public Map<String, Object> connect(Long workspaceId, String platform, String rawDomain, Map<String, String> credentials, String name) {
        Map<String, Object> response = new HashMap<>();

        String domain = normalizeDomain(platform, rawDomain);

        String error = StoreUrlGuard.validate(platform, domain);
        if (error != null) {
            response.put("ok", false);
            response.put("message", error);
            response.put("store", null);
            return response;
        }

        EcommerceStore store = storeRepository.findByWorkspaceIdAndPlatformAndDomain(workspaceId, platform, domain)
                .orElseGet(() -> {
                    EcommerceStore s = new EcommerceStore();
                    s.setWorkspaceId(workspaceId);
                    s.setPlatform(platform);
                    s.setDomain(domain);
                    s.setUuid(UUID.randomUUID().toString());
                    return s;
                });

        Map<String, Object> mergedCredentials = store.getCredentials() != null ? new HashMap<>(store.getCredentials()) : new HashMap<>();
        if (credentials != null) {
            for (Map.Entry<String, String> entry : credentials.entrySet()) {
                String val = entry.getValue();
                if (val != null && !val.isBlank() && !val.startsWith("•")) {
                    mergedCredentials.put(entry.getKey(), val);
                }
            }
        }

        String storeName = name != null && !name.isBlank() ? name : (store.getName() != null && !store.getName().isBlank() ? store.getName() : capitalize(platform) + " Store");
        store.setName(storeName);
        store.setCredentials(mergedCredentials);
        if (store.getWebhookSecret() == null || store.getWebhookSecret().isBlank()) {
            store.setWebhookSecret(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }

        storeRepository.save(store);

        Map<String, Object> testResult = connectionTester.test(store);
        boolean ok = Boolean.TRUE.equals(testResult.get("ok"));

        response.put("ok", ok);
        response.put("message", testResult.get("message"));
        response.put("store", store);
        return response;
    }

    public static String normalizeDomain(String platform, String domain) {
        if (domain == null) return "";
        domain = domain.trim();

        if ("bigcommerce".equalsIgnoreCase(platform)) {
            Matcher m = Pattern.compile("stores/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(domain);
            if (m.find()) {
                domain = m.group(1);
            }
            return domain;
        }

        if ("shopify".equalsIgnoreCase(platform)) {
            domain = domain.replaceAll("^https?://", "");
            int slashIndex = domain.indexOf('/');
            if (slashIndex != -1) {
                domain = domain.substring(0, slashIndex);
            }
            return domain;
        }

        if (!domain.matches("(?i)^https?://.*")) {
            domain = "https://" + domain;
        }

        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        return domain;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
