package com.seb.mortgage.property;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The values a property can have, in the order the property panel lists them. Type,
 * price and location are required; size, rooms and year built are only ever read from a
 * listing (see {@code docs/architecture.md} §4).
 */
public enum PropertyKey {

	TYPE("type", Kind.TYPE, true),
	PRICE("price", Kind.AMOUNT, true),
	LOCATION("location", Kind.TEXT, true),
	SIZE("size", Kind.AREA, false),
	ROOMS("rooms", Kind.COUNT, false),
	YEAR_BUILT("yearBuilt", Kind.YEAR, false);

	/** The kinds of property the banks tell apart. */
	public enum Type { APARTMENT, HOUSE, PLOT, NEW_BUILD }

	private enum Kind { TYPE, AMOUNT, TEXT, AREA, COUNT, YEAR }

	private final String key;
	private final Kind kind;
	private final boolean required;

	PropertyKey(String key, Kind kind, boolean required) {
		this.key = key;
		this.kind = kind;
		this.required = required;
	}

	public String key() {
		return key;
	}

	/** Required values can come from the chat too; the others only from a listing. */
	public boolean required() {
		return required;
	}

	public static Optional<PropertyKey> fromKey(String key) {
		return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
	}

	/**
	 * Turns a raw value into the stored form ("APARTMENT", "189000", "54.3", "1975"), or
	 * throws with a message the advisor can act on.
	 */
	public String normalize(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException(key + " needs a value");
		}
		switch (kind) {
			case TYPE -> {
				String name = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
				try {
					return Type.valueOf(name).name();
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(key + " must be one of apartment, house, plot or new_build");
				}
			}
			case AMOUNT, AREA -> {
				BigDecimal amount = parseNumber(value);
				if (amount.signum() <= 0) {
					throw new IllegalArgumentException(key + " must be more than 0");
				}
				return amount.stripTrailingZeros().toPlainString();
			}
			case COUNT -> {
				BigDecimal count = parseNumber(value);
				if (count.signum() <= 0 || count.stripTrailingZeros().scale() > 0) {
					throw new IllegalArgumentException(key + " must be a whole number of 1 or more");
				}
				return count.toBigInteger().toString();
			}
			case YEAR -> {
				BigDecimal year = parseNumber(value);
				if (year.stripTrailingZeros().scale() > 0 || year.intValue() < 1500
						|| year.intValue() > Year.now().getValue() + 5) {
					throw new IllegalArgumentException(key + " must be a year like 1975");
				}
				return year.toBigInteger().toString();
			}
			default -> {
				return value;
			}
		}
	}

	private BigDecimal parseNumber(String value) {
		String digits = value.replace("€", "").replace("m²", "").replace(" ", "").replace(" ", "");
		try {
			return new BigDecimal(digits);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					key + " must be a plain number like 189000, without thousands separators or text");
		}
	}
}
