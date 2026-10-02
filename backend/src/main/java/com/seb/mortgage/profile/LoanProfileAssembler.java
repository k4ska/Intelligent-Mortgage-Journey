package com.seb.mortgage.profile;

import com.seb.mortgage.application.LoanApplication;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.DocumentChecklistService;
import com.seb.mortgage.mockdata.PropertyValuationService;
import com.seb.mortgage.mockdata.RateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Turns the free-form answers on a {@link LoanApplication} into the standardized
 * {@link LoanProfile}, filling gaps with mock-data services and derived financials.
 *
 * <p>Missing answers are filled with defaults so the profile is always complete. Agents
 * get {@link #facts} instead, which leaves those defaults out.
 */
@Service
public class LoanProfileAssembler {

	/** Answers that {@link #assemble} replaces with a default when they are missing. */
	static final List<String> DEFAULTED_KEYS = List.of(
			"loanPurpose", "propertyAddress", "propertyType", "purchasePrice", "employmentType",
			"monthlyGrossIncome", "monthlyDebtPayments", "downPayment", "loanAmount", "termYears",
			"rateType");

	private static final List<String> LOAN_INPUTS = List.of("purchasePrice", "downPayment", "loanAmount");
	private static final List<String> PAYMENT_INPUTS = List.of(
			"purchasePrice", "downPayment", "loanAmount", "termYears", "rateType", "fixedYears");
	private static final List<String> AFFORDABILITY_INPUTS = Stream.concat(PAYMENT_INPUTS.stream(),
			Stream.of("monthlyGrossIncome", "monthlyDebtPayments")).toList();

	private final AffordabilityCalculator calculator;
	private final PropertyValuationService valuationService;
	private final RateService rateService;
	private final DocumentChecklistService documentChecklistService;

	public LoanProfileAssembler(AffordabilityCalculator calculator,
			PropertyValuationService valuationService,
			RateService rateService,
			DocumentChecklistService documentChecklistService) {
		this.calculator = calculator;
		this.valuationService = valuationService;
		this.rateService = rateService;
		this.documentChecklistService = documentChecklistService;
	}

	public LoanProfile assemble(LoanApplication application) {
		Map<String, Object> a = application.getAnswers();

		double purchasePrice = num(a.get("purchasePrice"), 250_000);
		double downPayment = num(a.get("downPayment"), 25_000);
		double loanAmount = Math.max(0, num(a.get("loanAmount"), purchasePrice - downPayment));
		int termYears = (int) num(a.get("termYears"), 25);
		String rateType = str(a.get("rateType"), "VARIABLE");
		Integer fixedYears = a.get("fixedYears") == null ? null : (int) num(a.get("fixedYears"), 0);

		double estimatedValue = valuationService
				.estimate(str(a.get("propertyAddress"), ""), purchasePrice)
				.estimatedValue();

		double monthlyIncome = num(a.get("monthlyGrossIncome"), 4_000);
		double monthlyDebt = num(a.get("monthlyDebtPayments"), 0);

		double rate = rateService.rateFor(rateType, fixedYears);
		double payment = calculator.monthlyPayment(loanAmount, rate, termYears);
		double stressed = calculator.stressedMonthlyPayment(loanAmount, rate, termYears);
		double buffer = round2(monthlyIncome - monthlyDebt - stressed);

		// LTV at origination is measured against the purchase price (what you agreed to pay).
		double ltv = calculator.loanToValue(loanAmount, purchasePrice);
		double dti = calculator.debtToIncome(monthlyDebt + payment, monthlyIncome);

		String stressResult = buffer < 0 ? "FAIL" : buffer < 300 ? "TIGHT" : "PASS";

		LoanProfile.Financials financials = new LoanProfile.Financials(
				downPayment, ltv, dti, round2(payment), round2(stressed), buffer, stressResult);

		LoanProfile.Applicant applicant = new LoanProfile.Applicant(
				str(a.get("employmentType"), "EMPLOYED"), monthlyIncome, monthlyDebt);

		LoanProfile.Property property = new LoanProfile.Property(
				str(a.get("propertyType"), "APARTMENT"),
				str(a.get("propertyLocation"), str(a.get("propertyAddress"), "Unknown")),
				purchasePrice, estimatedValue);

		LoanProfile.LoanRequest loanRequest = new LoanProfile.LoanRequest(
				loanAmount, termYears, rateType, str(a.get("loanPurpose"), "PURCHASE"));

		return new LoanProfile(
				List.of(applicant),
				property,
				loanRequest,
				financials,
				riskIndicators(ltv, dti, financials, purchasePrice, estimatedValue),
				documentChecklistService.requiredDocuments(a));
	}

	/**
	 * The application as the agents may see it: only the answers the user really gave, and
	 * only the derived figures whose inputs are all real. See {@link ProfileFacts}.
	 */
	public ProfileFacts facts(LoanApplication application) {
		Map<String, Object> entered = new LinkedHashMap<>();
		application.getAnswers().forEach((key, value) -> {
			if (value != null && !value.toString().isBlank()) {
				entered.put(key, value);
			}
		});
		List<String> notAnsweredYet = DEFAULTED_KEYS.stream().filter(k -> !entered.containsKey(k)).toList();

		LoanProfile p = assemble(application);
		LoanProfile.Financials f = p.financials();
		double purchasePrice = p.property().purchasePrice();
		double estimatedValue = p.property().estimatedValue();

		boolean hasPrice = entered.containsKey("purchasePrice");
		boolean hasLoan = entered.containsKey("loanAmount") || (hasPrice && entered.containsKey("downPayment"));
		boolean hasPayment = hasLoan && entered.containsKey("termYears") && entered.containsKey("rateType");
		boolean hasAffordability = hasPayment && entered.containsKey("monthlyGrossIncome")
				&& entered.containsKey("monthlyDebtPayments");

		List<ProfileFacts.Figure> figures = new ArrayList<>();
		if (hasPrice) {
			figures.add(new ProfileFacts.Figure("estimatedPropertyValue", estimatedValue,
					List.of("purchasePrice", "propertyAddress"), valuationRisk(purchasePrice, estimatedValue)));
		}
		if (hasLoan && !entered.containsKey("loanAmount")) {
			figures.add(new ProfileFacts.Figure("loanAmountFromPriceMinusDownPayment",
					p.loanRequest().amount(), LOAN_INPUTS, null));
		}
		if (hasPrice && hasLoan) {
			figures.add(new ProfileFacts.Figure("loanToValuePercent", f.ltv(), LOAN_INPUTS, ltvRisk(f.ltv())));
		}
		if (hasPayment) {
			figures.add(new ProfileFacts.Figure("estimatedMonthlyPayment", f.estimatedMonthlyPayment(),
					PAYMENT_INPUTS, null));
			figures.add(new ProfileFacts.Figure("stressedMonthlyPayment", f.stressedMonthlyPayment(),
					PAYMENT_INPUTS, null));
		}
		if (hasAffordability) {
			figures.add(new ProfileFacts.Figure("debtToIncomePercent", f.dti(), AFFORDABILITY_INPUTS,
					dtiRisk(f.dti())));
			figures.add(new ProfileFacts.Figure("monthlyBufferAfterStress", f.monthlyBufferAfterStress(),
					AFFORDABILITY_INPUTS, null));
			figures.add(new ProfileFacts.Figure("stressTestResult", f.stressTestResult(),
					AFFORDABILITY_INPUTS, stressRisk(f)));
		}
		return new ProfileFacts(entered, notAnsweredYet, figures, p.documents());
	}

	private static List<String> riskIndicators(double ltv, double dti, LoanProfile.Financials f,
			double purchasePrice, double estimatedValue) {
		List<String> risks = Stream.of(valuationRisk(purchasePrice, estimatedValue), ltvRisk(ltv),
				dtiRisk(dti), stressRisk(f)).filter(Objects::nonNull).toList();
		return risks.isEmpty()
				? List.of("No major risk indicators from the information provided so far")
				: risks;
	}

	private static String valuationRisk(double purchasePrice, double estimatedValue) {
		return estimatedValue < purchasePrice * 0.97
				? "The automated valuation (" + (long) estimatedValue + ") is below the price you "
						+ "agreed (" + (long) purchasePrice + ") - a lender may lend against the lower figure"
				: null;
	}

	private static String ltvRisk(double ltv) {
		return ltv > 90 ? "High loan-to-value (" + ltv + "%) - limited lender choice and a higher rate" : null;
	}

	private static String dtiRisk(double dti) {
		return dti > 40 ? "Debt-to-income of " + dti + "% is above the level most lenders are comfortable with" : null;
	}

	private static String stressRisk(LoanProfile.Financials f) {
		if ("FAIL".equals(f.stressTestResult())) {
			return "Fails the rate-rise stress test - monthly costs would exceed income if rates rose "
					+ (int) AffordabilityCalculator.STRESS_DELTA_PCT + " points";
		}
		if ("TIGHT".equals(f.stressTestResult())) {
			return "Thin buffer (" + f.monthlyBufferAfterStress() + "/month) after a rate-rise stress test";
		}
		return null;
	}

	private static double num(Object o, double fallback) {
		if (o instanceof Number n) {
			return n.doubleValue();
		}
		if (o != null) {
			try {
				return Double.parseDouble(o.toString().replace(",", "").trim());
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return fallback;
	}

	private static String str(Object o, String fallback) {
		return o == null || o.toString().isBlank() ? fallback : o.toString();
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
