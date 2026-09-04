package com.whatsmine.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Converter
public class JsonListMapConverter implements AttributeConverter<List<Map<String, Object>>, String> {

    private static final Logger logger = LoggerFactory.getLogger(JsonListMapConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            logger.error("Could not convert list to JSON string", e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.error("Could not parse JSON string to list: {}", dbData, e);
            return new ArrayList<>();
        }
    }
}
