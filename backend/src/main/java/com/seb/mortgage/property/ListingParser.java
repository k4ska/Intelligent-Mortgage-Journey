package com.seb.mortgage.property;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a listing page's schema.org structured data (its {@code application/ld+json}
 * blocks) into property values. Best effort: whatever can't be found or doesn't make sense
 * is left out, and the advisor asks the user for it instead.
 */
final class ListingParser {

	private static final Pattern LD_JSON = Pattern.compile(
			"<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	/** schema.org types that say what kind of property it is. */
	private static final Map<String, PropertyKey.Type> SCHEMA_TYPES = Map.of(
			"Apartment", PropertyKey.Type.APARTMENT,
			"Accommodation", PropertyKey.Type.APARTMENT,
			"House", PropertyKey.Type.HOUSE,
			"SingleFamilyResidence", PropertyKey.Type.HOUSE);

	private ListingParser() {
	}

	/** The values found on the page, normalized; empty if it has no usable structured data. */
	static Map<PropertyKey, String> parse(String html, String url) {
		List<JsonNode> blocks = new ArrayList<>();
		Matcher matcher = LD_JSON.matcher(html == null ? "" : html);
		while (matcher.find()) {
			try {
				blocks.add(JSON.readTree(matcher.group(1).trim()));
			} catch (JacksonException e) {
				// A broken block says nothing; the others may still be fine.
			}
		}

		Map<PropertyKey, String> values = new EnumMap<>(PropertyKey.class);
		if (blocks.isEmpty()) {
			return values;
		}
		JsonNode root = JSON.valueToTree(blocks);
		put(values, PropertyKey.TYPE, type(root, url));
		put(values, PropertyKey.PRICE, price(root));
		put(values, PropertyKey.LOCATION, find(root, "address").map(ListingParser::address).orElse(null));
		put(values, PropertyKey.SIZE, find(root, "floorSize").map(ListingParser::quantity).orElse(null));
		put(values, PropertyKey.ROOMS, find(root, "numberOfRooms").map(ListingParser::quantity).orElse(null));
		put(values, PropertyKey.YEAR_BUILT, find(root, "yearBuilt").map(ListingParser::scalar).orElse(null));
		return values;
	}

	private static void put(Map<PropertyKey, String> values, PropertyKey key, String raw) {
		if (raw == null) {
			return;
		}
		try {
			values.put(key, key.normalize(raw));
		} catch (IllegalArgumentException e) {
			// Not a value we can use (e.g. "3+" rooms); the advisor asks if it matters.
		}
	}

	/**
	 * From the schema.org type if it says, otherwise from the words kv.ee uses in the
	 * listing's name and link (Estonian or English). New builds are checked first, since a
	 * new-build apartment counts as a new build.
	 */
	private static String type(JsonNode root, String url) {
		StringBuilder words = new StringBuilder(url == null ? "" : url);
		PropertyKey.Type fromSchema = null;
		for (JsonNode node : nodes(root)) {
			for (JsonNode type : list(node.path("@type"))) {
				if (fromSchema == null) {
					String name = scalar(type);
					fromSchema = name == null ? null : SCHEMA_TYPES.get(name);
				}
			}
			words.append(' ').append(Optional.ofNullable(scalar(node.path("name"))).orElse(""))
					.append(' ').append(Optional.ofNullable(scalar(node.path("category"))).orElse(""));
		}
		String text = words.toString().toLowerCase(Locale.ROOT);
		if (text.contains("uusarendus") || text.contains("new development") || text.contains("new-development")
				|| text.contains("new build") || text.contains("new-build")) {
			return PropertyKey.Type.NEW_BUILD.name();
		}
		if (fromSchema != null) {
			return fromSchema.name();
		}
		if (text.contains("korter") || text.contains("apartment")) {
			return PropertyKey.Type.APARTMENT.name();
		}
		if (text.contains("maatükk") || text.contains("maatukk") || text.contains("krunt") || text.contains("plot")) {
			return PropertyKey.Type.PLOT.name();
		}
		if (text.contains("maja") || text.contains("house")) {
			return PropertyKey.Type.HOUSE.name();
		}
		return null;
	}

	/** The first price given in euros (or without a currency). */
	private static String price(JsonNode root) {
		for (JsonNode node : nodes(root)) {
			String price = scalar(node.path("price"));
			String currency = scalar(node.path("priceCurrency"));
			if (price != null && (currency == null || currency.equalsIgnoreCase("EUR"))) {
				return price;
			}
		}
		return null;
	}

	/** "Street 1, Town" from a PostalAddress, or the address as written. */
	private static String address(JsonNode address) {
		if (!address.isObject()) {
			return scalar(address);
		}
		String street = scalar(address.path("streetAddress"));
		String town = Optional.ofNullable(scalar(address.path("addressLocality")))
				.orElse(scalar(address.path("addressRegion")));
		List<String> parts = new ArrayList<>();
		for (String part : new String[] { street, town }) {
			if (part != null && !part.isBlank() && parts.stream().noneMatch(p -> p.contains(part.trim()))) {
				parts.add(part.trim());
			}
		}
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	/** A plain number, a QuantitativeValue's value, or the number in "54.3 m²". */
	private static String quantity(JsonNode node) {
		String text = node.isObject() ? scalar(node.path("value")) : scalar(node);
		if (text == null) {
			return null;
		}
		Matcher number = Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(text);
		return number.find() ? number.group().replace(',', '.') : null;
	}

	/** The first value of the field anywhere in the data, depth first. */
	private static Optional<JsonNode> find(JsonNode root, String field) {
		return nodes(root).stream().map(node -> node.path(field)).filter(v -> !v.isMissingNode() && !v.isNull())
				.findFirst();
	}

	/** Every object in the data, depth first in document order. */
	private static List<JsonNode> nodes(JsonNode root) {
		List<JsonNode> found = new ArrayList<>();
		collect(root, found);
		return found;
	}

	private static void collect(JsonNode node, List<JsonNode> found) {
		if (node.isObject()) {
			found.add(node);
		}
		if (node.isContainer()) {
			node.values().forEach(child -> collect(child, found));
		}
	}

	private static List<JsonNode> list(JsonNode node) {
		return node.isArray() ? List.copyOf(node.values()) : List.of(node);
	}

	private static String scalar(JsonNode node) {
		return node.isString() || node.isNumber() ? node.asString() : null;
	}
}
