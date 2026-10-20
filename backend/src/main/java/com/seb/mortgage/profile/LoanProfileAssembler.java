package com.seb.mortgage.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.AffordabilityCalculator.LoanLimit;
import com.seb.mortgage.mockdata.AffordabilityCalculator.MaxLoan;
import com.seb.mortgage.mockdata.RateService;

/**
 * Builds the {@link LoanProfile} from the user's profile field rows and property values,
 * and runs the affordability checks on it (#65). Only confirmed values the user gave are
 * used: a fact that is missing or still uncertain is reported, never filled with a
 * default (rule 2 in {@code docs/architecture.md} §1).
 *
 * <p>The property comes in as its value rows, so this package doesn't depend on the
 * property package (which already depends on this one).
 */
@Service
public class LoanProfileAssembler {

	/** The loan term the checks assume, the same as the banks' offers. */
	public static final int TERM_YEARS = 25;

	/** The longest term Eesti Pank's rules allow for a housing loan. */
	public static final int MAX_TERM_YEARS = 30;

	/** The rate a check assumes: the variable rate plus a bank margin. */
	public record Terms(double variableRatePct, double marginPct, double ratePct) {}

	/** What a check needs that the user hasn't given, or has given but not confirmed yet. */
	public record Missing(List<String> missing, List<String> unconfirmed) implements Check, Range {}

	public sealed interface Check permits Missing, Checked {}

	/**
	 * The loan profile for the user's property. Its {@code riskIndicators} are exactly the
	 * checks the loan fails, so the property is affordable when there are none.
	 *
	 * @param maxLoan the largest loan the rules allow, whatever the property
	 */
	public record Checked(LoanProfile profile, MaxLoan maxLoan, Terms terms) implements Check {
		public boolean affordable() {
			return profile.riskIndicators().isEmpty();
		}
	}

	public sealed interface Range permits Missing, PriceRange {}

	/**
	 * The most the user could pay for a home, putting in all their savings.
	 *
	 * @param standard the largest loan over {@link #TERM_YEARS}
	 * @param longest  the largest loan over {@link #MAX_TERM_YEARS}
	 */
	public record PriceRange(double savings, MaxLoan standard, MaxLoan longest, Terms terms) implements Range {
		public double standardMaxPrice() {
			return standard.amount() + savings;
		}

		public double longestMaxPrice() {
			return longest.amount() + savings;
		}
	}

	static final String DOWN_PAYMENT_TOO_LOW = "The down payment is below "
			+ (100 - (int) AffordabilityCalculator.TYPICAL_MAX_LTV_PCT) + "% of the price";
	static final String PAYMENTS_TOO_HIGH = "All loan payments would be over "
			+ (int) AffordabilityCalculator.MAX_DTI_PCT + "% of net income at the stress-test rate";
	static final String BUFFER_TOO_SMALL = "Less than " + (int) AffordabilityCalculator.MIN_STRESS_BUFFER
			+ " a month would be left after debts, living costs and the payment at the stress-test rate";

	private final AffordabilityCalculator calculator;
	private final RateService rates;

	public LoanProfileAssembler(AffordabilityCalculator calculator, RateService rates) {
		this.calculator = calculator;
		this.rates = rates;
	}

	/** The variable rate plus the given bank margin. */
	public Terms terms(double marginPct) {
		double variable = rates.rateFor("VARIABLE", null);
		return new Terms(variable, marginPct, round2(variable + marginPct));
	}

	/** Whether the user can afford the property, or what is still missing to tell. */
	public Check check(List<ProfileFieldView> fields, List<ProfileFieldView> property, Terms terms) {
		Reader reader = new Reader(fields, property);
		Facts facts = reader.facts();
		Double price = reader.amount("price", true);
		if (facts == null || price == null) {
			return reader.missing();
		}

		double income = facts.householdIncome();
		double creditCost = calculator.creditLimitMonthlyCost(facts.creditLimits());
		double debts = facts.debts() + creditCost;
		double livingCosts = calculator.livingCosts(facts.livingCosts(), facts.adults(), facts.dependants());
		double downPayment = Math.min(facts.savings(), price);
		double loan = price - downPayment;
		double payment = calculator.monthlyPayment(loan, terms.ratePct(), TERM_YEARS);
		double stressedPayment = calculator.stressedMonthlyPayment(loan, terms.ratePct(), TERM_YEARS);
		double stressedDti = calculator.debtToIncome(debts + stressedPayment, income);
		double buffer = income - debts - livingCosts - stressedPayment;

		List<String> risks = new ArrayList<>();
		if (calculator.loanToValue(loan, price) > AffordabilityCalculator.TYPICAL_MAX_LTV_PCT) {
			risks.add(DOWN_PAYMENT_TOO_LOW);
		}
		if (stressedDti > AffordabilityCalculator.MAX_DTI_PCT) {
			risks.add(PAYMENTS_TOO_HIGH);
		}
		if (buffer < AffordabilityCalculator.MIN_STRESS_BUFFER) {
			risks.add(BUFFER_TOO_SMALL);
		}

		LoanProfile profile = new LoanProfile(
				applicants(facts),
				new LoanProfile.Household(facts.adults(), facts.dependants(), facts.livingCosts(),
						calculator.minimumLivingCosts(facts.adults(), facts.dependants())),
				new LoanProfile.Property(reader.text("type"), reader.text("location"), price, price),
				new LoanProfile.LoanRequest(round2(loan), TERM_YEARS, "VARIABLE", "purchase"),
				new LoanProfile.Financials(round2(downPayment), calculator.loanToValue(loan, price),
						calculator.debtToIncome(debts + payment, income), stressedDti, round2(payment),
						round2(stressedPayment), round2(livingCosts), creditCost, round2(buffer),
						buffer >= AffordabilityCalculator.MIN_STRESS_BUFFER ? "PASS" : buffer >= 0 ? "TIGHT" : "FAIL"),
				List.copyOf(risks),
				List.of());
		return new Checked(profile, maxLoan(facts, terms, TERM_YEARS), terms);
	}

	/** The price range the user could afford, whatever property they have in mind. */
	public Range range(List<ProfileFieldView> fields, Terms terms) {
		Reader reader = new Reader(fields, List.of());
		Facts facts = reader.facts();
		if (facts == null) {
			return reader.missing();
		}
		return new PriceRange(facts.savings(), maxLoan(facts, terms, TERM_YEARS),
				maxLoan(facts, terms, MAX_TERM_YEARS), terms);
	}

	private MaxLoan maxLoan(Facts facts, Terms terms, int termYears) {
		if (facts.savings() <= 0) {
			// Without a down payment no lender goes to 100% loan-to-value.
			return new MaxLoan(0, LoanLimit.DOWN_PAYMENT);
		}
		double debts = facts.debts() + calculator.creditLimitMonthlyCost(facts.creditLimits());
		double livingCosts = calculator.livingCosts(facts.livingCosts(), facts.adults(), facts.dependants());
		return calculator.maxLoan(facts.householdIncome(), debts, livingCosts, facts.savings(), terms.ratePct(),
				termYears);
	}

	private static List<LoanProfile.Applicant> applicants(Facts facts) {
		LoanProfile.Applicant primary = new LoanProfile.Applicant(null, facts.employment(), facts.netIncome(),
				facts.debts(), facts.creditLimits());
		return facts.coApplicant()
				? List.of(primary, new LoanProfile.Applicant(null, null, facts.coApplicantNetIncome(), 0, 0))
				: List.of(primary);
	}

	/** The household's facts, all given and confirmed. */
	private record Facts(double netIncome, String employment, double savings, double debts, double creditLimits,
			boolean coApplicant, double coApplicantNetIncome, int dependants, double livingCosts) {
		double householdIncome() {
			return netIncome + (coApplicant ? coApplicantNetIncome : 0);
		}

		int adults() {
			return coApplicant ? 2 : 1;
		}
	}

	/** Reads values by key, noting each one that is missing or uncertain, in the order asked. */
	private static final class Reader {
		private final Map<String, ProfileFieldView> fields;
		private final Map<String, ProfileFieldView> property;
		private final List<String> missing = new ArrayList<>();
		private final List<String> unconfirmed = new ArrayList<>();

		Reader(List<ProfileFieldView> fields, List<ProfileFieldView> property) {
			this.fields = byKey(fields);
			this.property = byKey(property);
		}

		private static Map<String, ProfileFieldView> byKey(List<ProfileFieldView> values) {
			return values.stream().collect(Collectors.toMap(ProfileFieldView::key, Function.identity(),
					(first, second) -> second));
		}

		/** The household's facts, or null if any is missing; the gaps are noted either way. */
		Facts facts() {
			Double netIncome = amount(ProfileKey.NET_INCOME.key(), false);
			Double savings = amount(ProfileKey.SAVINGS.key(), false);
			Double debts = amount(ProfileKey.MONTHLY_DEBT_PAYMENTS.key(), false);
			Double creditLimits = amount(ProfileKey.CREDIT_CARD_LIMITS.key(), false);
			String coApplicant = confirmed(fields, ProfileKey.HAS_CO_APPLICANT.key());
			Double coApplicantNetIncome = "yes".equals(coApplicant)
					? amount(ProfileKey.CO_APPLICANT_NET_INCOME.key(), false) : Double.valueOf(0);
			Double dependants = amount(ProfileKey.DEPENDANTS.key(), false);
			Double livingCosts = amount(ProfileKey.MONTHLY_LIVING_COSTS.key(), false);
			if (!missing.isEmpty() || !unconfirmed.isEmpty()) {
				return null;
			}
			ProfileFieldView employment = fields.get(ProfileKey.EMPLOYMENT.key());
			return new Facts(netIncome, employment == null ? null : employment.value(), savings, debts, creditLimits,
					"yes".equals(coApplicant), coApplicantNetIncome, dependants.intValue(), livingCosts);
		}

		Double amount(String key, boolean fromProperty) {
			String value = confirmed(fromProperty ? property : fields, key);
			if (value == null) {
				return null;
			}
			try {
				return Double.valueOf(value);
			} catch (NumberFormatException e) {
				missing.add(key);
				return null;
			}
		}

		/** A property value if there is one, confirmed or not: only shown, never calculated with. */
		String text(String key) {
			ProfileFieldView value = property.get(key);
			return value == null ? null : value.value();
		}

		private String confirmed(Map<String, ProfileFieldView> values, String key) {
			ProfileFieldView value = values.get(key);
			if (value == null || value.value().isBlank()) {
				missing.add(key);
				return null;
			}
			if (value.status() != ProfileField.Status.CONFIRMED) {
				unconfirmed.add(key);
				return null;
			}
			return value.value();
		}

		Missing missing() {
			return new Missing(List.copyOf(missing), List.copyOf(unconfirmed));
		}
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
