package com.seb.mortgage.application;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persists a set of step ids as one comma-separated column. Step ids never contain commas. */
@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

	@Override
	public String convertToDatabaseColumn(Set<String> attribute) {
		return attribute == null ? "" : String.join(",", attribute);
	}

	@Override
	public Set<String> convertToEntityAttribute(String dbData) {
		Set<String> set = new LinkedHashSet<>();
		if (dbData != null && !dbData.isBlank()) {
			set.addAll(Arrays.asList(dbData.split(",")));
		}
		return set;
	}
}
