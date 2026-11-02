package com.seb.mortgage.bank;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.RateService;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.property.PropertyView;

/**
 * Decides the financial result of a bank application. The bank agent only writes the
 * result; it cannot choose any amount, rate, payment or negotiation limit.
 *
 * <p>All values are prototype terms and are not an offer from a real bank.
 */
@Service
public class OfferCalculator {

	public enum DecisionType { QUESTION, DECLINE, OFFER }

	public record DocumentSnapshot(String type, String status) {}

	/** The only input the bank side needs from an application. */
	public record Snapshot(List<ProfileFieldView> fields, PropertyView property,
			List<DocumentSnapshot> documents) {
		public Snapshot {
			fields = fields == null ? List.of() : List.copyOf(fields);
			documents = documents == null ? List.of() : List.copyOf(documents);
		}

		public Snapshot(List<ProfileFieldView> fields, PropertyView property) {
			this(fields, property, List.of());
		}
	}

	public record Offer(double amount, int termYears, double euriborPct, double marginPct,
			double ratePct, double monthlyPayment, List<String> conditions, LocalDate validUntil) {
		public Offer {
			conditions = List.copyOf(conditions);
		}
	}

	public record Decision(DecisionType type, String reason, Offer offer) {
		public static Decision question(String reason) {
			return new Decision(DecisionType.QUESTION, reason, null);
		}

		public static Decision decline(String reason) {
			return new Decision(DecisionType.DECLINE, reason, null);
		}

		public static Decision offer(Offer offer) {
			return new Decision(DecisionType.OFFER, null, offer);
		}
	}

	private static final int TERM_YEARS = 25;
	private static final double EPSILON = 0.0001;

	private final RateService rates;
	private final AffordabilityCalculator affordability;

	public OfferCalculator(RateService rates, AffordabilityCalculator affordability) {
		this.rates = rates;
		this.affordability = affordability;
	}

	public Decision calculate(Snapshot snapshot, BankCatalogService.Bank bank) {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(bank, "bank");

		Map<String, String> fields = snapshot.fields().stream()
				.collect(Collectors.toMap(ProfileFieldView::key, ProfileFieldView::value, (first, second) -> second));
		if (!hasNumber(fields, "netIncome")) {
			return Decision.question("the household's monthly net income");
		}
		if (!hasNumber(fields, "savings")) {
			return Decision.question("the available down payment");
		}
		if (snapshot.property() == null) {
			return Decision.question("the property price and details");
		}
		double price = number(snapshot.property(), "price");
		if (price <= 0) {
			return Decision.question("a valid property price");
		}
		if (snapshot.documents().stream().anyMatch(document -> "valuation".equalsIgnoreCase(document.type()))
				&& snapshot.documents().stream().noneMatch(document ->
						"valuation".equalsIgnoreCase(document.type())
								&& "confirmed".equalsIgnoreCase(document.status()))) {
			return Decision.question("a confirmed valuation report");
		}

		double savings = number(fields, "savings");
		double minimumDownPayment = price * bank.minDownPaymentPct() / 100.0;
		if (savings + EPSILON < minimumDownPayment) {
			return Decision.decline("The available down payment is below this bank's minimum of "
					+ bank.minDownPaymentPct() + "%.");
		}

		double euribor = rates.rateFor("VARIABLE", null);
		double rate = euribor + bank.baseMarginPct();
		double income = number(fields, "netIncome");
		if ("yes".equalsIgnoreCase(fields.get("hasCoApplicant"))
				&& hasNumber(fields, "coApplicantNetIncome")) {
			income += number(fields, "coApplicantNetIncome");
		}
		double debts = numberOrZero(fields, "monthlyDebtPayments")
				+ affordability.creditLimitMonthlyCost(numberOrZero(fields, "creditCardLimits"));
		int dependants = (int) numberOrZero(fields, "dependants");
		int adults = "yes".equalsIgnoreCase(fields.get("hasCoApplicant")) ? 2 : 1;
		Double enteredLivingCosts = hasNumber(fields, "monthlyLivingCosts") ? number(fields, "monthlyLivingCosts") : null;
		double livingCosts = affordability.livingCosts(enteredLivingCosts, adults, dependants);
		double neededLoan = Math.max(0, price - savings);
		AffordabilityCalculator.MaxLoan maxLoan = affordability.maxLoan(income, debts, livingCosts,
				null, rate, TERM_YEARS);
		double ltvLoan = price * bank.maxLtvPct() / 100.0;
		if (neededLoan > ltvLoan + EPSILON) {
			return Decision.decline("The requested loan exceeds this bank's maximum loan-to-value of "
					+ bank.maxLtvPct() + "%.");
		}
		if (neededLoan > maxLoan.amount() + EPSILON) {
			return Decision.decline("The requested loan does not pass the bank's affordability assessment.");
		}

		double payment = affordability.monthlyPayment(neededLoan, rate, TERM_YEARS);
		List<String> conditions = List.of("Valuation confirms the property value",
				"Final approval is subject to document verification");
		return Decision.offer(new Offer(round2(neededLoan), TERM_YEARS, euribor, bank.baseMarginPct(),
				round2(rate), round2(payment), conditions, LocalDate.now().plusDays(30)));
	}

	/**
	 * The same offer at another margin (negotiation): the rate and the monthly payment
	 * follow, and it is valid for 30 days from today. The amount and term stay.
	 */
	public Offer withMargin(Offer offer, double marginPct) {
		double rate = offer.euriborPct() + marginPct;
		double payment = affordability.monthlyPayment(offer.amount(), rate, offer.termYears());
		return new Offer(offer.amount(), offer.termYears(), offer.euriborPct(), round2(marginPct), round2(rate),
				round2(payment), offer.conditions(), LocalDate.now().plusDays(30));
	}

	public double lowestMargin(BankCatalogService.Bank bank, int round) {
		if (round < 0 || round > bank.maxNegotiationRounds()) {
			throw new IllegalArgumentException("Negotiation round must be between 0 and "
					+ bank.maxNegotiationRounds());
		}
		if (bank.maxNegotiationRounds() == 0) {
			return round2(bank.baseMarginPct());
		}
		double step = (bank.baseMarginPct() - bank.lowestMarginPct()) / bank.maxNegotiationRounds();
		return round2(Math.max(bank.lowestMarginPct(), bank.baseMarginPct() - step * round));
	}

	private static boolean hasNumber(Map<String, String> fields, String key) {
		return fields.containsKey(key) && !fields.get(key).isBlank() && number(fields, key) >= 0;
	}

	private static double number(Map<String, String> fields, String key) {
		try {
			return Double.parseDouble(fields.get(key));
		} catch (RuntimeException e) {
			return -1;
		}
	}

	private static double number(PropertyView property, String key) {
		return property.fields().stream().filter(field -> field.key().equals(key)).findFirst()
				.map(ProfileFieldView::value).map(OfferCalculator::parse).orElse(-1.0);
	}

	private static double parse(String value) {
		try {
			return Double.parseDouble(value);
		} catch (RuntimeException e) {
			return -1;
		}
	}

	private static double numberOrZero(Map<String, String> fields, String key) {
		double value = number(fields, key);
		return value < 0 ? 0 : value;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
