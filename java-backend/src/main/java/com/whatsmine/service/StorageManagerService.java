package com.whatsmine.service;

import com.whatsmine.model.IntegrationConfig;
import com.whatsmine.repository.IntegrationConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StorageManagerService {

    private final IntegrationConfigRepository integrationConfigRepository;

    public StorageManagerService(IntegrationConfigRepository integrationConfigRepository) {
        this.integrationConfigRepository = integrationConfigRepository;
    }

    public String diskName() {
        return integrationConfigRepository.findByEnabledTrue().stream()
                .filter(ic -> ic.getProvider() != null && ic.getProvider().startsWith("storage_"))
                .findFirst()
                .map(ic -> {
                    if ("storage_s3".equals(ic.getProvider())) return "s3";
                    if ("storage_do".equals(ic.getProvider())) return "digitalocean";
                    if ("storage_wasabi".equals(ic.getProvider())) return "wasabi";
                    return "public";
                })
                .orElse("public");
    }

    public String directoryPrefix() {
        return integrationConfigRepository.findByEnabledTrue().stream()
                .filter(ic -> ic.getProvider() != null && ic.getProvider().startsWith("storage_"))
                .findFirst()
                .map(ic -> {
                    Map<String, Object> creds = ic.getCredentials();
                    if (creds != null && creds.get("directory_prefix") != null) {
                        String p = creds.get("directory_prefix").toString().replaceAll("^/|/$", "");
                        return p.isEmpty() ? "" : p + "/";
                    }
                    return "";
                })
                .orElse("");
    }

    public String prefixedPath(String path) {
        String prefix = directoryPrefix();
        return prefix.isEmpty() ? path : prefix + path.replaceAll("^/", "");
    }
}
