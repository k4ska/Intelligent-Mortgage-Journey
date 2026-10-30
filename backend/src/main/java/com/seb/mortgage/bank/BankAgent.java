package com.seb.mortgage.bank;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * One bank's side of an email thread, configured by the bank it's called with. It sees only
 * the frozen application snapshot and that bank's rules, never the advisor conversation.
 *
 * <p>Skeleton: the first reply is canned. The LLM version comes with the offer issues.
 */
@Component
public class BankAgent {

	public record Draft(String type, String subject, String body) {}

	private static final String SUBJECT = "Your mortgage application is received";

	private final ApplicationSnapshots snapshots;
	private final OfferCalculator calculator;
	private final BankEmailVerifier verifier;

	public BankAgent(ApplicationSnapshots snapshots, OfferCalculator calculator, ObjectMapper mapper) {
		this.snapshots = snapshots;
		this.calculator = calculator;
		this.verifier = new BankEmailVerifier(mapper);
	}

	public Draft initialReply(BankCatalogService.Bank bank, String snapshotJson) {
		OfferCalculator.Decision decision = decision(bank, snapshotJson);
		return verifier.verify(draft(bank, decision), decision, bank);
	}

	public OfferCalculator.Decision decision(BankCatalogService.Bank bank, String snapshotJson) {
		return calculator.calculate(snapshots.read(snapshotJson), bank);
	}

	private Draft draft(BankCatalogService.Bank bank, OfferCalculator.Decision decision) {
		return switch (decision.type()) {
			case QUESTION -> new Draft("question", SUBJECT, bank.greeting()
					+ " Before we can assess it, please send us " + decision.reason());
			case DECLINE -> new Draft("decline", "Your mortgage application from " + bank.name(),
					bank.greeting() + " We are unable to approve this application because "
							+ decision.reason());
			case OFFER -> offerDraft(bank, decision.offer());
		};
	}

	/**
	 * The offer email, with the figures written the way the inbox shows them: "€140 000",
	 * "1.75%", "€910.60". {@link BankEmailVerifier} reads them back the same way.
	 */
	static Draft offerDraft(BankCatalogService.Bank bank, OfferCalculator.Offer offer) {
		return new Draft("offer", "Your mortgage offer from " + bank.name(),
				bank.greeting() + " We can offer " + euros(offer.amount()) + " over " + offer.termYears()
						+ " years at a total rate of " + percent(offer.ratePct()) + " (Euribor "
						+ percent(offer.euriborPct()) + " plus a margin of " + percent(offer.marginPct())
						+ "). The estimated monthly payment is " + cents(offer.monthlyPayment())
						+ ". The offer is valid until " + offer.validUntil() + ".");
	}

	/** Whole euros with a space between thousands: "€140 000". */
	private static String euros(double amount) {
		return "€" + String.format(Locale.ROOT, "%,.0f", amount).replace(',', ' ');
	}

	/** Euros and cents: "€910.60". */
	private static String cents(double amount) {
		return "€" + String.format(Locale.ROOT, "%,.2f", amount).replace(',', ' ');
	}

	/** A rate without trailing zeros: "6.1%", "1.75%". */
	private static String percent(double rate) {
		return BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString() + "%";
	}
}
