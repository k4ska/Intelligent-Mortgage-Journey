package com.seb.mortgage.advisor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers the user has actually written in the conversation, so a tool can refuse a
 * value the advisor made up (#88). Generous on format ("3300", "3 300", "3,300", "3.3k",
 * "two") and strict on substance: a number the user never wrote is never accepted.
 */
final class StatedNumbers {

	/** A number as people type it: digits with optional thousands or decimal separators, and "k". */
	private static final Pattern NUMBER = Pattern.compile(
			"\\d[\\d \\u00a0.,]*\\d|\\d", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern THOUSANDS_SUFFIX = Pattern.compile(
			"^\\s*(k|tk|tuh\\.?|tuhat|thousand)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
	private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
			Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
			Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
			Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10),
			Map.entry("null", 0), Map.entry("üks", 1), Map.entry("kaks", 2), Map.entry("kolm", 3),
			Map.entry("neli", 4), Map.entry("viis", 5), Map.entry("kuus", 6), Map.entry("seitse", 7),
			Map.entry("kaheksa", 8), Map.entry("üheksa", 9), Map.entry("kümme", 10));
	private static final Pattern WORD = Pattern.compile("\\p{L}+", Pattern.UNICODE_CHARACTER_CLASS);
	/** Ways of saying "none" that make 0 a stated value, but only in the message being answered. */
	private static final Pattern NONE = Pattern.compile(
			"\\b(no|none|nothing|zero|don't have|do not have|haven't got|ei ole|pole|puudub|mitte midagi)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

	private final Set<BigDecimal> numbers = new HashSet<>();

	private StatedNumbers() {
	}

	/**
	 * @param earlier the user's earlier messages the advisor can see
	 * @param current the message the advisor is answering now
	 */
	static StatedNumbers from(List<String> earlier, String current) {
		StatedNumbers stated = new StatedNumbers();
		List<String> texts = new ArrayList<>(earlier);
		texts.add(current == null ? "" : current);
		texts.forEach(stated::read);
		// "No" only counts as 0 right after the advisor asked, not anywhere in the conversation.
		if (current != null && NONE.matcher(current).find()) {
			stated.numbers.add(BigDecimal.ZERO);
		}
		return stated;
	}

	/** Whether the user wrote this (already normalized) number. */
	boolean contains(String value) {
		try {
			BigDecimal number = new BigDecimal(value).stripTrailingZeros();
			return numbers.contains(number);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void read(String text) {
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			String token = matcher.group();
			boolean thousands = THOUSANDS_SUFFIX.matcher(text.substring(matcher.end())).find();
			for (BigDecimal number : readings(token)) {
				add(number);
				if (thousands) {
					add(number.multiply(BigDecimal.valueOf(1000)));
				}
			}
		}
		Matcher words = WORD.matcher(text.toLowerCase(Locale.ROOT));
		while (words.find()) {
			Integer number = NUMBER_WORDS.get(words.group());
			if (number != null) {
				add(BigDecimal.valueOf(number));
			}
		}
	}

	/**
	 * Every way the token could be meant: separators as thousands ("3,300" = 3300), the last
	 * one as a decimal point ("3,5" = 3.5), and each space-separated part on its own ("2 3").
	 */
	private static List<BigDecimal> readings(String token) {
		List<BigDecimal> readings = new ArrayList<>();
		readings.add(new BigDecimal(token.replaceAll("[^\\d]", "")));
		int last = Math.max(token.lastIndexOf('.'), token.lastIndexOf(','));
		if (last > 0) {
			String whole = token.substring(0, last).replaceAll("[^\\d]", "");
			String fraction = token.substring(last + 1).replaceAll("[^\\d]", "");
			if (!whole.isEmpty() && !fraction.isEmpty()) {
				readings.add(new BigDecimal(whole + "." + fraction));
			}
		}
		for (String part : token.split("[ \\u00a0]+")) {
			String digits = part.replaceAll("[^\\d]", "");
			if (!digits.isEmpty() && !part.equals(token)) {
				readings.add(new BigDecimal(digits));
			}
		}
		return readings;
	}

	private void add(BigDecimal number) {
		numbers.add(number.stripTrailingZeros());
	}
}
