package com.seb.mortgage.flow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Contract 1 of 3 (see README): the flow definition served to the frontend as JSON.
 *
 * <p>The frontend renders the journey generically from this structure, so steps, fields
 * and branching can change without a frontend release. Adaptive routing lives in
 * {@link Step#next()}.
 */
public record FlowDefinition(
		String version,
		List<Step> steps
) {

	public record Step(
			String id,
			String title,
			String subtitle,
			List<Field> fields,
			/** Ordered rules; the first whose condition matches wins, else the {@code default} entry. */
			List<NextRule> next
	) {}

	public record Field(
			String key,
			FieldType type,
			String label,
			/** Key the frontend passes to POST /api/agent/explain to get a plain-language explanation. */
			String helpKey,
			String placeholder,
			/** Populated for {@link FieldType#SELECT} / {@link FieldType#RADIO}. */
			List<Option> options,
			Validation validation
	) {}

	public enum FieldType { TEXT, MONEY, NUMBER, PERCENT, DATE, SELECT, RADIO, BOOLEAN }

	public record Option(String value, String label) {}

	public record Validation(
			Boolean required,
			Double min,
			Double max,
			String pattern
	) {}

	/**
	 * A branching rule. Either {@code condition}+{@code target} (go to {@code target} when the
	 * condition holds) or {@code defaultTarget} (fallback when no earlier rule matched).
	 */
	public record NextRule(
			@JsonProperty("if") Condition condition,
			@JsonProperty("goto") String target,
			@JsonProperty("default") String defaultTarget
	) {}

	/** {@code op} is one of: eq, ne, gt, gte, lt, lte, in. */
	public record Condition(String field, String op, Object value) {

		@SuppressWarnings("unchecked")
		public boolean matches(Map<String, Object> answers) {
			Object actual = answers.get(field);
			return switch (op) {
				case "eq" -> equalsLoose(actual, value);
				case "ne" -> !equalsLoose(actual, value);
				case "gt" -> compare(actual, value) > 0;
				case "gte" -> compare(actual, value) >= 0;
				case "lt" -> compare(actual, value) < 0;
				case "lte" -> compare(actual, value) <= 0;
				case "in" -> value instanceof List<?> list && list.stream().anyMatch(v -> equalsLoose(actual, v));
				default -> throw new IllegalArgumentException("Unknown condition op: " + op);
			};
		}

		private static boolean equalsLoose(Object a, Object b) {
			if (a == null || b == null) return a == b;
			if (a instanceof Number && b instanceof Number) {
				return ((Number) a).doubleValue() == ((Number) b).doubleValue();
			}
			return a.toString().equalsIgnoreCase(b.toString());
		}

		private static int compare(Object a, Object b) {
			if (a == null) return -1;
			return Double.compare(toDouble(a), toDouble(b));
		}

		private static double toDouble(Object o) {
			if (o instanceof Number n) return n.doubleValue();
			return Double.parseDouble(o.toString());
		}
	}
}
