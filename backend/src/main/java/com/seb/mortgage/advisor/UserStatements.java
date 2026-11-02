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
 * What the user has actually written in the conversation, so a tool can refuse a value the
 * advisor made up (#88): the numbers in it, and whether a quote really is the user's words.
 * Generous on format ("3300", "3 300", "3,300", "3.3k", "two") and strict on substance: a
 * number the user never wrote, or a quote they never said, is never accepted.
 */
final class UserStatements {

	/**
	 * A number as people type it: digits with optional thousands or decimal separators, and "k".
	 * A separator only joins digits, so in "198 000, 52m2" the comma and space end the number
	 * instead of making it 198000.52 (#117).
	 */
	private static final Pattern NUMBER = Pattern.compile(
			"\\d(?:[ \\u00a0.,]?\\d)*", Pattern.UNICODE_CHARACTER_CLASS);
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

	/**
	 * Words that make a figure a guess ("about 2400", "idk maybe 1200", "~2000", "umbes 900"),
	 * as they read once simplified (#140).
	 */
	private static final List<String> HEDGES = List.of("roughly", "approximately", "approx", "maybe", "probably",
			"perhaps", "i think", "i guess", "idk", "i don t know", "not sure", "something like", "or so", "ish",
			"give or take", "more or less", "vist", "arvatavasti", "vast");
	/**
	 * Words that only hedge right before a number: "about 2400" is a guess, but "thinking
	 * about buying" says nothing about any value.
	 */
	private static final Pattern BEFORE_A_NUMBER = Pattern.compile(
			" (about|around|circa|ca|tilde|umbes|ligikaudu|kuskil) \\d");
	/**
	 * Where one clause of a message ends: a comma or semicolon before a space, the end of a
	 * sentence, "and" or "but". A hedge only covers its own clause, so in "no kids, idk maybe
	 * 1200 on living" it covers the 1200 but not the "no kids". A comma between digits
	 * ("3,300") doesn't split.
	 */
	private static final Pattern CLAUSE_END = Pattern.compile(
			"[,;](?=\\s)|[.!?](?=\\s|$)|\\n|\\b(and|but|ja|aga)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

	private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

	private final Set<BigDecimal> numbers = new HashSet<>();
	/** Each of the user's messages as they wrote them, oldest first. */
	private final List<String> messages = new ArrayList<>();
	/** All the user's messages, simplified for quote matching. */
	private final StringBuilder words = new StringBuilder(" ");
	/** Only the message being answered, simplified the same way. */
	private String currentWords = " ";

	private UserStatements() {
	}

	/**
	 * @param earlier the user's earlier messages the advisor can see
	 * @param current the message the advisor is answering now
	 */
	static UserStatements from(List<String> earlier, String current) {
		UserStatements stated = new UserStatements();
		List<String> texts = new ArrayList<>(earlier);
		texts.add(current == null ? "" : current);
		texts.forEach(stated::read);
		texts.forEach(text -> stated.words.append(simplify(text)).append(' '));
		stated.messages.addAll(texts);
		stated.currentWords = " " + simplify(current == null ? "" : current) + " ";
		// "No" only counts as 0 right after the advisor asked, not anywhere in the conversation.
		if (current != null && NONE.matcher(current).find()) {
			stated.numbers.add(BigDecimal.ZERO);
		}
		return stated;
	}

	/**
	 * Whether the user said this, word for word, ignoring case and punctuation ("I work at
	 * a bank" matches "i work at a bank!"). Only whole words count, so "a" doesn't match "bank".
	 */
	boolean containsQuote(String quote) {
		String simplified = quote == null ? "" : simplify(quote);
		return !simplified.isEmpty() && words.indexOf(" " + simplified + " ") >= 0;
	}

	/** Like {@link #containsQuote}, but only the message being answered counts (a "yes" from earlier doesn't). */
	boolean saidNow(String quote) {
		String simplified = quote == null ? "" : simplify(quote);
		return !simplified.isEmpty() && currentWords.contains(" " + simplified + " ");
	}

	/**
	 * Whether the user only guessed this value: a word like "about" or "maybe" in the clause
	 * of their message that states it (#140). The message is the latest one with the quote;
	 * the clause is the one with the number, for a number, or the quote's own clauses.
	 * "I earn about 2400, my partner earns 2100" hedges the 2400, not the 2100.
	 */
	boolean hedged(String quote, String value) {
		String simplified = quote == null ? "" : simplify(quote);
		if (simplified.isEmpty()) {
			return false;
		}
		for (int i = messages.size() - 1; i >= 0; i--) {
			String message = messages.get(i);
			if (!(" " + simplify(message) + " ").contains(" " + simplified + " ")) {
				continue;
			}
			List<String> clauses = List.of(CLAUSE_END.split(message));
			List<String> stating = clauses.stream().filter(clause -> states(clause, value)).toList();
			if (stating.isEmpty()) {
				stating = clauses.stream().filter(clause -> overlaps(simplify(clause), simplified)).toList();
			}
			return stating.stream().anyMatch(UserStatements::hedges);
		}
		return false;
	}

	/** Whether this clause has the number, read the same generous way as {@link #containsNumber}. */
	private static boolean states(String clause, String value) {
		if (value == null || !value.matches("\\d+(\\.\\d+)?")) {
			return false;
		}
		UserStatements one = new UserStatements();
		one.read(clause);
		return one.containsNumber(value);
	}

	/** Whether a clause and a quote share words: the clause is part of the quote, or the other way round. */
	private static boolean overlaps(String clause, String quote) {
		return !clause.isEmpty() && ((" " + quote + " ").contains(" " + clause + " ")
				|| (" " + clause + " ").contains(" " + quote + " "));
	}

	private static boolean hedges(String clause) {
		String words = " " + simplify(clause.replace("~", " tilde ")) + " ";
		return HEDGES.stream().anyMatch(hedge -> words.contains(" " + hedge + " "))
				|| BEFORE_A_NUMBER.matcher(words).find();
	}

	private static String simplify(String text) {
		return NOT_A_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
	}

	/** Whether the user wrote this (already normalized) number. */
	boolean containsNumber(String value) {
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
