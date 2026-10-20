package com.seb.mortgage.profile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The facts the advisor may save to a user's profile, in the order the profile panel
 * lists them. The advisor can't invent new keys; the skeleton starts with the basics
 * and later issues add more (property, loan amount, ...). When adding one, describe it
 * in the advisor's {@code updateProfile} tool too.
 */
public enum ProfileKey {

	NET_INCOME("netIncome", Type.AMOUNT),
	EMPLOYMENT("employment", Type.TEXT),
	SAVINGS("savings", Type.AMOUNT),
	MONTHLY_DEBT_PAYMENTS("monthlyDebtPayments", Type.AMOUNT),
	CREDIT_CARD_LIMITS("creditCardLimits", Type.AMOUNT),
	HAS_CO_APPLICANT("hasCoApplicant", Type.YES_NO),
	CO_APPLICANT_NET_INCOME("coApplicantNetIncome", Type.AMOUNT),
	DEPENDANTS("dependants", Type.COUNT),
	MONTHLY_LIVING_COSTS("monthlyLivingCosts", Type.AMOUNT);

	public enum Type { AMOUNT, COUNT, YES_NO, TEXT }

	private final String key;
	private final Type type;

	ProfileKey(String key, Type type) {
		this.key = key;
		this.type = type;
	}

	public String key() {
		return key;
	}

	public Type type() {
		return type;
	}

	public static Optional<ProfileKey> fromKey(String key) {
		return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
	}

	/**
	 * Turns what the advisor sent into the stored form ("2350", "yes", "2", "employed"),
	 * or throws with a message the advisor can act on.
	 */
	public String normalize(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException(key + " needs a value");
		}
		switch (type) {
			case AMOUNT -> {
				BigDecimal amount = parseNumber(value);
				if (amount.signum() < 0) {
					throw new IllegalArgumentException(key + " can't be negative");
				}
				return amount.stripTrailingZeros().toPlainString();
			}
			case COUNT -> {
				BigDecimal count = parseNumber(value);
				if (count.signum() < 0 || count.stripTrailingZeros().scale() > 0) {
					throw new IllegalArgumentException(key + " must be a whole number of 0 or more");
				}
				return count.toBigInteger().toString();
			}
			case YES_NO -> {
				return switch (value.toLowerCase(Locale.ROOT)) {
					case "yes", "true" -> "yes";
					case "no", "false" -> "no";
					default -> throw new IllegalArgumentException(key + " must be yes or no");
				};
			}
			default -> {
				return value;
			}
		}
	}

	private BigDecimal parseNumber(String value) {
		String digits = value.replace("€", "").replace(" ", "").replace(" ", "");
		try {
			return new BigDecimal(digits);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					key + " must be a plain number like 2350, without thousands separators or text");
		}
	}
}
