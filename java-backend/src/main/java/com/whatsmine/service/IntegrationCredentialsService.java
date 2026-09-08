package com.whatsmine.service;

import com.whatsmine.model.IntegrationConfig;
import com.whatsmine.repository.IntegrationConfigRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read/write access to system-level integration credentials (Meta app,
 * LLM providers, etc.) — the storage side backing the admin Integrations
 * page. IntegrationConfig.credentials is AES-GCM encrypted at rest via
 * EncryptedJsonMapConverter; callers here only ever see plain Maps.
 */
@Service
public class IntegrationCredentialsService {

    private static final String DEFAULT_MODE = "live";

    private final IntegrationConfigRepository repository;

    public IntegrationCredentialsService(IntegrationConfigRepository repository) {
        this.repository = repository;
    }

    public IntegrationConfig find(String provider) {
        return repository.findByProviderAndMode(provider, DEFAULT_MODE).orElse(null);
    }

    public boolean isEnabled(String provider) {
        IntegrationConfig config = find(provider);
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /** Credential fields for a provider, or an empty map if unconfigured. */
    public Map<String, String> getCredentials(String provider) {
        IntegrationConfig config = find(provider);
        if (config == null || config.getCredentials() == null) return Map.of();

        Map<String, String> result = new LinkedHashMap<>();
        config.getCredentials().forEach((k, v) -> result.put(k, v != null ? v.toString() : null));
        return result;
    }

    public String getCredential(String provider, String key) {
        return getCredentials(provider).get(key);
    }

    /**
     * Merge-saves credential fields for a provider. A blank/null value for a
     * key already on file leaves the existing value untouched (so re-saving
     * a form that only shows placeholders for secrets doesn't wipe them).
     */
    public IntegrationConfig saveCredentials(String provider, String label, Map<String, String> fields, boolean enabled, String mode) {
        IntegrationConfig config = repository.findByProviderAndMode(provider, mode).orElseGet(() -> {
            IntegrationConfig c = new IntegrationConfig();
            c.setProvider(provider);
            c.setMode(mode);
            return c;
        });

        Map<String, Object> existing = new LinkedHashMap<>();
        if (config.getCredentials() != null) existing.putAll(config.getCredentials());
        if (fields != null) {
            fields.forEach((k, v) -> {
                if (v != null && !v.isBlank()) existing.put(k, v);
            });
        }

        config.setCredentials(existing);
        if (label != null) config.setLabel(label);
        config.setEnabled(enabled);

        return repository.save(config);
    }

    /** Records the outcome of a "Test Connection" attempt for the admin Integrations page. */
    public void recordTestResult(String provider, boolean ok, String message) {
        IntegrationConfig config = find(provider);
        if (config == null) return;
        config.setLastTestedAt(LocalDateTime.now());
        config.setLastTestStatus(ok ? "ok" : "fail");
        config.setLastTestMessage(message);
        repository.save(config);
    }

    /** Overwrites a single credential field (e.g. rotating a webhook verify token) without touching the rest. */
    public void updateCredentialField(String provider, String key, String value) {
        IntegrationConfig config = find(provider);
        if (config == null) return;
        Map<String, Object> existing = new LinkedHashMap<>();
        if (config.getCredentials() != null) existing.putAll(config.getCredentials());
        existing.put(key, value);
        config.setCredentials(existing);
        repository.save(config);
    }
}
