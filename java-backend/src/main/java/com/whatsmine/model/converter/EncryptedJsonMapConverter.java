package com.whatsmine.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.security.CredentialsCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Same shape as {@link JsonAttributeConverter} (Map&lt;String,Object&gt; <-> JSON
 * column), but the JSON is AES-GCM encrypted at rest via {@link CredentialsCipher}
 * — for columns holding real secrets (API tokens, app secrets), where
 * JsonAttributeConverter's plaintext storage isn't acceptable.
 */
@Converter
public class EncryptedJsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedJsonMapConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return CredentialsCipher.encrypt(objectMapper.writeValueAsString(attribute));
        } catch (Exception e) {
            logger.error("Could not encrypt credentials map", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        String json = CredentialsCipher.decrypt(dbData);
        if (json == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.error("Could not parse decrypted credentials JSON", e);
            return new HashMap<>();
        }
    }
}
