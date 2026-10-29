package com.seb.mortgage.bank;

import java.util.Locale;
import java.util.regex.Pattern;
import java.math.BigDecimal;

import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic guard for bank emails. Bank agents may choose wording, but all financial
 * figures in an offer must match the calculator's result. Invalid drafts use a safe template.
 */
public class BankEmailVerifier {

	private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d+(?:[.,]\\d+)?");
	private final ObjectMapper mapper;

	public BankEmailVerifier(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public BankAgent.Draft verify(BankAgent.Draft draft, OfferCalculator.Decision decision,
			BankCatalogService.Bank bank) {
		if (decision.type() != OfferCalculator.DecisionType.OFFER || decision.offer() == null) {
			return draft;
		}
		OfferCalculator.Offer offer = decision.offer();
		String expected = numbers(offer);
		String actual = numbers(draft.body());
		if (actual.equals(expected)) {
			return draft;
		}
		return new BankAgent.Draft("offer", "Your mortgage offer from " + bank.name(),
				bank.greeting() + " We can offer €" + format(offer.amount()) + " over "
						+ offer.termYears() + " years at a total rate of " + format(offer.ratePct())
						+ "% (Euribor " + format(offer.euriborPct()) + "% plus margin "
						+ format(offer.marginPct()) + "). The estimated monthly payment is €"
						+ format(offer.monthlyPayment())
						+ ". The offer is valid until " + offer.validUntil() + ".");
	}

	private String numbers(OfferCalculator.Offer offer) {
		return numbers("€" + offer.amount() + " " + offer.termYears() + " " + offer.ratePct()
				+ " " + offer.euriborPct() + " " + offer.marginPct() + " €" + offer.monthlyPayment()
				+ " " + offer.validUntil());
	}

	private String numbers(String text) {
		var matcher = NUMBER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			if (result.length() > 0) result.append(',');
			result.append(new BigDecimal(matcher.group().replace(',', '.'))
					.stripTrailingZeros().toPlainString());
		}
		return result.toString();
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
