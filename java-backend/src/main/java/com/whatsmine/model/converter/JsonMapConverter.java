package com.whatsmine.model.converter;

import jakarta.persistence.Converter;

/**
 * Alias for JsonAttributeConverter — used for Map&lt;String, Object&gt; JSON columns.
 */
@Converter
public class JsonMapConverter extends JsonAttributeConverter {
}
