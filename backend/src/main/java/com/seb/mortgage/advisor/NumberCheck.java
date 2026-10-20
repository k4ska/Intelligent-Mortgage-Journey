package com.seb.mortgage.advisor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer 2 of keeping the advisor honest ({@code docs/architecture.md} §3): every euro amount
 * and percentage in a reply must appear in what the advisor was given (#67). Plain code,
 * so it's deterministic, free and instant. Rounding within {@link #TOLERANCE} is allowed, so
 * "about €210,000" passes for 209 734.
 */
final class NumberCheck {

	/** How far a figure may be from its source, as a share of the source. */
	static final double TOLERANCE = 0.01;

	/** @param unsupported each figure in the reply that no source has, as written there */
	record Result(List<String> unsupported) {
		boolean passed() {
			return unsupported.isEmpty();
		}
	}

	/** A number as written: digit groups with thousands separators, or plain digits, with an optional decimal part. */
	private static final String NUMBER = "\\d{1,3}(?:[ \\u00a0\\u202f,.]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?";
	/** "k" or "million" after a number; not the "m" of "m²". */
	private static final String SCALE = "(?:\\s?(k|thousand|tuhat|m|mln|million)(?![\\w²]))?";
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern EURO_BEFORE = Pattern.compile("(?:€|\\beur\\b)\\s?(" + NUMBER + ")" + SCALE, FLAGS);
	private static final Pattern EURO_AFTER = Pattern.compile(
			"(" + NUMBER + ")" + SCALE + "\\s?(?:€|\\b(?:eur|euros?|eurot)\\b)", FLAGS);
	private static final Pattern PERCENT = Pattern.compile(
			"(" + NUMBER + ")\\s?(?:%|\\bper ?cent\\b|\\bprotsenti\\b)", FLAGS);
	/** A range with the scale or sign written once, at the end: "€146–151k", "3–4%". */
	private static final Pattern EURO_RANGE = Pattern.compile(
			"(?:€|\\beur\\b)\\s?(" + NUMBER + ")\\s?[–-]\\s?(?:€\\s?)?(" + NUMBER + ")" + SCALE, FLAGS);
	private static final Pattern PERCENT_RANGE = Pattern.compile(
			"(" + NUMBER + ")\\s?[–-]\\s?(" + NUMBER + ")\\s?(?:%|\\bper ?cent\\b|\\bprotsenti\\b)", FLAGS);
	/** Any number in a source, with an optional "k" or "thousand" after it. */
	private static final Pattern ANY = Pattern.compile("(" + NUMBER + ")" + SCALE, FLAGS);

	private NumberCheck() {
	}

	/**
	 * @param reply   the advisor's finished reply
	 * @param sources everything the advisor may take figures from this turn
	 */
	static Result check(String reply, List<String> sources) {
		Set<BigDecimal> known = new LinkedHashSet<>();
		for (String source : sources) {
			if (source == null) {
				continue;
			}
			Matcher matcher = ANY.matcher(source);
			while (matcher.find()) {
				for (BigDecimal reading : readings(matcher.group(1))) {
					known.add(scaled(reading, matcher.group(2)));
				}
			}
		}
		List<String> unsupported = new ArrayList<>();
		for (Figure figure : figures(reply == null ? "" : reply)) {
			if (known.stream().noneMatch(source -> close(figure.value(), source))) {
				unsupported.add(figure.written());
			}
		}
		return new Result(List.copyOf(unsupported));
	}

	/** A euro amount or percentage in the reply. */
	record Figure(String written, BigDecimal value) {}

	static List<Figure> figures(String reply) {
		List<Figure> figures = new ArrayList<>();
		// Each stretch of text counts once, even if two patterns match it ("€2,350 EUR").
		boolean[] taken = new boolean[reply.length()];
		for (Pattern pattern : List.of(EURO_RANGE, PERCENT_RANGE)) {
			Matcher matcher = pattern.matcher(reply);
			while (matcher.find()) {
				for (int i = matcher.start(); i < matcher.end(); i++) {
					taken[i] = true;
				}
				String scale = pattern == PERCENT_RANGE ? null : matcher.group(3);
				for (int end = 1; end <= 2; end++) {
					figures.add(new Figure(matcher.group().trim(), scaled(parse(matcher.group(end)), scale)));
				}
			}
		}
		for (Pattern pattern : List.of(EURO_BEFORE, EURO_AFTER, PERCENT)) {
			Matcher matcher = pattern.matcher(reply);
			while (matcher.find()) {
				if (taken[matcher.start(1)]) {
					continue;
				}
				for (int i = matcher.start(); i < matcher.end(); i++) {
					taken[i] = true;
				}
				String scale = pattern == PERCENT ? null : matcher.group(2);
				figures.add(new Figure(matcher.group().trim(), scaled(parse(matcher.group(1)), scale)));
			}
		}
		return figures;
	}

	/**
	 * How the reply means a number: spaces always separate thousands; a comma or point
	 * followed by groups of exactly three digits does too ("2,350", "1.168"); otherwise
	 * the last one is the decimal point ("3.5", "3,5", "976.50").
	 */
	static BigDecimal parse(String token) {
		String digits = token.replaceAll("[ \\u00a0\\u202f]", "");
		if (digits.matches("\\d{1,3}([,.]\\d{3})+")) {
			return new BigDecimal(digits.replaceAll("[,.]", ""));
		}
		int last = Math.max(digits.lastIndexOf(','), digits.lastIndexOf('.'));
		if (last < 0) {
			return new BigDecimal(digits);
		}
		String whole = digits.substring(0, last).replaceAll("[,.]", "");
		return new BigDecimal((whole.isEmpty() ? "0" : whole) + "." + digits.substring(last + 1));
	}

	/** Every way a source could mean a number, so a source never fails a figure on format alone. */
	private static List<BigDecimal> readings(String token) {
		List<BigDecimal> readings = new ArrayList<>();
		readings.add(parse(token));
		readings.add(new BigDecimal(token.replaceAll("\\D", "")));
		return readings;
	}

	private static BigDecimal scaled(BigDecimal number, String scale) {
		if (scale == null) {
			return number;
		}
		return switch (scale.toLowerCase(Locale.ROOT)) {
			case "k", "thousand", "tuhat" -> number.multiply(BigDecimal.valueOf(1_000));
			default -> number.multiply(BigDecimal.valueOf(1_000_000));
		};
	}

	private static boolean close(BigDecimal figure, BigDecimal source) {
		if (source.signum() == 0) {
			return figure.signum() == 0;
		}
		BigDecimal allowed = source.abs().multiply(BigDecimal.valueOf(TOLERANCE));
		return figure.subtract(source).abs().compareTo(allowed) <= 0;
	}
}
