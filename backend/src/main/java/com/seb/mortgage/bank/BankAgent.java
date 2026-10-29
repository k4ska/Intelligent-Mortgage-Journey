package com.seb.mortgage.bank;

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
			case OFFER -> {
				OfferCalculator.Offer offer = decision.offer();
				yield new Draft("offer", "Your mortgage offer from " + bank.name(),
						bank.greeting() + " We can offer €" + offer.amount() + " over "
								+ offer.termYears() + " years at a total rate of " + offer.ratePct()
								+ "% (Euribor " + offer.euriborPct() + "% plus margin "
								+ offer.marginPct() + "). The estimated monthly payment is €" + offer.monthlyPayment()
								+ ". The offer is valid until " + offer.validUntil() + ".");
			}
		};
	}
}
