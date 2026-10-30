package com.seb.mortgage.bank;

import java.util.regex.Pattern;
import java.math.BigDecimal;

import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic guard for bank emails. Bank agents may choose wording, but all financial
 * figures in an offer must match the calculator's result. Invalid drafts use a safe template.
 */
public class BankEmailVerifier {

	/** A number, with thousands optionally grouped by spaces ("140 000"). */
	private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d{1,3}(?: \\d{3})+(?:[.,]\\d+)?|(?<![\\w.])-?\\d+(?:[.,]\\d+)?");
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
		return BankAgent.offerDraft(bank, offer);
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
			result.append(new BigDecimal(matcher.group().replace(" ", "").replace(',', '.'))
					.stripTrailingZeros().toPlainString());
		}
		return result.toString();
	}
}
