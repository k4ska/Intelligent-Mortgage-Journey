package com.seb.mortgage.bank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.seb.mortgage.profile.LoanProfileAssembler;

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
	static final double CONTRACT_FEE_PCT = 0.5;
	static final double MIN_CONTRACT_FEE = 300;

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
		return new Draft("offer", "Your mortgage offer from " + bank.name(), bank.greeting() + " " + terms(offer));
	}

	/**
	 * A better offer after the user asked for one (negotiation). The calculator set the
	 * margin; {@code last} says the bank can't go lower, and {@code competing} that the user
	 * brought up another bank's offer. It is an {@code offer} email, so the user can accept it
	 * like the first one. Each bank writes it in its own tone (#144); the openings have no
	 * figures, so the verifier still checks only the terms.
	 */
	public Draft counterOffer(BankCatalogService.Bank bank, OfferCalculator.Offer offer, boolean last,
			boolean competing) {
		String body = counterOpening(bank) + (competing ? " " + competingLine(bank) : "") + " " + terms(offer)
				+ (last ? " " + finalLine(bank) : "");
		Draft draft = new Draft("offer", "Our updated offer from " + bank.name(), body);
		return verifier.verify(draft, OfferCalculator.Decision.offer(offer), bank);
	}

	private static String counterOpening(BankCatalogService.Bank bank) {
		return switch (bank.id()) {
			case "swedbank" -> "Thanks for getting back to us! We took another look and we can make you a better offer.";
			case "lhv" -> "We have reviewed your request. Our margin is lowered.";
			case "luminor" -> "Thank you for your message. Having reconsidered your application, we are pleased to "
					+ "offer you a reduced margin.";
			case "coop" -> "Thank you for asking, we're glad you did. We have looked at your application again and "
					+ "can lower our margin for you.";
			default -> "Thank you for your message. We have looked at your application again and can lower our margin.";
		};
	}

	private static String competingLine(BankCatalogService.Bank bank) {
		return switch (bank.id()) {
			case "swedbank" -> "We know you have another offer on the table, so we've gone as low as we can.";
			case "lhv" -> "Given the competing offer you mentioned, this is our lowest margin.";
			case "luminor" -> "We have taken into account the offer you received from another bank.";
			case "coop" -> "We understand another bank has made you an offer, so we've done our best to beat it.";
			default -> "We have taken the other bank's offer you mentioned into account and gone to our lowest margin.";
		};
	}

	private static String finalLine(BankCatalogService.Bank bank) {
		return switch (bank.id()) {
			case "lhv" -> "This is our final offer.";
			case "swedbank" -> "This is our final offer, and we'd love to have you with us.";
			default -> "This is our final offer.";
		};
	}

	/** The answer when the user asks for more but the bank is already at its lowest margin. */
	public Draft noBetterOffer(BankCatalogService.Bank bank) {
		String body = switch (bank.id()) {
			case "swedbank" -> "Thanks for asking! Our current offer is already the best we can do, so it stays as "
					+ "it is. You're welcome to accept it while it is valid.";
			case "lhv" -> "Our current offer is already our best. It stays as it is and can be accepted while valid.";
			case "luminor" -> "Thank you for your message. We regret that our current offer is already the best we "
					+ "are able to make, so it stays as it is. You are welcome to accept it while it is valid.";
			case "coop" -> "Thank you for your message. We've looked again, but our current offer is already the "
					+ "best we can do, so it stays as it is. You are welcome to accept it while it is valid.";
			default -> "Thank you for your message. Our current offer is already the best we can do, so it stays "
					+ "as it is. You are welcome to accept it while it is valid.";
		};
		return new Draft("message", "Re: your mortgage offer from " + bank.name(), body);
	}

	/**
	 * Short, fixed answers to the questions a reply asks (#129), from the bank's rules and the
	 * offer that stands. Fees and early repayment are the same simple rules at every bank.
	 */
	public Draft answers(BankCatalogService.Bank bank, List<NegotiationAgent.Question> questions,
			OfferCalculator.Offer offer) {
		StringBuilder body = new StringBuilder("Thank you for your questions.");
		for (NegotiationAgent.Question question : questions) {
			body.append("\n\n").append(switch (question) {
				case EARLY_REPAYMENT -> "Early repayment: you can pay extra or repay the whole loan at any time. "
						+ "With a variable rate there is no fee for it; just let us know a month ahead.";
				case LONGER_TERM -> offer.termYears() >= LoanProfileAssembler.MAX_TERM_YEARS
						? "A longer term: " + offer.termYears() + " years is already the longest term we offer."
						: "A longer term: we lend for up to " + LoanProfileAssembler.MAX_TERM_YEARS + " years. Over "
								+ LoanProfileAssembler.MAX_TERM_YEARS + " years the estimated monthly payment would be "
								+ cents(calculator.monthlyPayment(offer, LoanProfileAssembler.MAX_TERM_YEARS))
								+ " instead of " + cents(offer.monthlyPayment()) + ", but you would pay more interest "
								+ "in total. We can set the term when we prepare the contract.";
				case DOCUMENTS -> "Documents: before signing we need your ID card or passport, proof of income for "
						+ "the last 6 months, a bank statement for the last 6 months, a valuation report from a "
						+ "valuator we accept, and the purchase or reservation agreement.";
				case FEES -> "Fees: the contract fee is " + percent(CONTRACT_FEE_PCT) + " of the loan, at least "
						+ euros(MIN_CONTRACT_FEE) + ", so " + euros(contractFee(offer.amount()))
						+ " for this loan. The valuator charges for the valuation report separately.";
			});
		}
		body.append("\n\nYou are welcome to accept the offer while it is valid.");
		return new Draft("message", "Re: your questions to " + bank.name(), body.toString());
	}

	/** The confirmation once the user accepts, with what happens next in real life (#129, vision step 12). */
	public Draft acceptance(BankCatalogService.Bank bank) {
		return new Draft("confirmation", "Your mortgage with " + bank.name() + ": next steps",
				"Thank you for choosing " + bank.name() + ". Here is what happens next:\n\n"
						+ "1. Order a valuation report from a valuator we accept, if you haven't yet.\n"
						+ "2. Arrange home insurance for the property; it must be in place before we pay out the loan.\n"
						+ "3. Book a time at a " + bank.name() + " branch to sign the loan contract, and bring your "
						+ "ID card.\n"
						+ "4. You and the seller sign the purchase contract at a notary, and we pay the loan to the "
						+ "seller.\n\n"
						+ "We will contact you within two working days to agree a time for signing.");
	}

	/** The contract fee: a share of the loan, but at least a minimum. */
	static double contractFee(double amount) {
		return Math.max(MIN_CONTRACT_FEE, Math.round(amount * CONTRACT_FEE_PCT / 100.0));
	}

	private static String terms(OfferCalculator.Offer offer) {
		return "We can offer " + euros(offer.amount()) + " over " + offer.termYears()
				+ " years at a total rate of " + percent(offer.ratePct()) + " (Euribor "
				+ percent(offer.euriborPct()) + " plus a margin of " + percent(offer.marginPct())
				+ "). The estimated monthly payment is " + cents(offer.monthlyPayment())
				+ ". The offer is valid until " + offer.validUntil() + ".";
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
