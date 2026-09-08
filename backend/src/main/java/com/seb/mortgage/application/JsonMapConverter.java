package com.seb.mortgage.application;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists the free-form answers map as a JSON string column. Portable across H2 and
 * Postgres without database-specific JSON types, which keeps the prototype simple.
 *
 * <p>Instantiated by the JPA provider, not Spring, so it uses a shared mapper rather than
 * an injected bean.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

	private static final ObjectMapper MAPPER = JsonMapper.shared();
	private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {};

	@Override
	public String convertToDatabaseColumn(Map<String, Object> attribute) {
		return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
	}

	@Override
	public Map<String, Object> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new LinkedHashMap<>();
		}
		return MAPPER.readValue(dbData, TYPE);
	}
}
