package com.seb.mortgage.profile;

import com.seb.mortgage.application.LoanApplication;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.DocumentChecklistService;
import com.seb.mortgage.mockdata.PropertyValuationService;
import com.seb.mortgage.mockdata.RateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the free-form answers on a {@link LoanApplication} into the standardized
 * {@link LoanProfile}, filling gaps with mock-data services and derived financials.
 */
@Service
public class LoanProfileAssembler {

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

	private static List<String> riskIndicators(double ltv, double dti, LoanProfile.Financials f,
			double purchasePrice, double estimatedValue) {
		List<String> risks = new ArrayList<>();
		if (estimatedValue < purchasePrice * 0.97) {
			risks.add("The automated valuation (" + (long) estimatedValue + ") is below the price you "
					+ "agreed (" + (long) purchasePrice + ") - a lender may lend against the lower figure");
		}
		if (ltv > 90) {
			risks.add("High loan-to-value (" + ltv + "%) - limited lender choice and a higher rate");
		}
		if (dti > 40) {
			risks.add("Debt-to-income of " + dti + "% is above the level most lenders are comfortable with");
		}
		if ("FAIL".equals(f.stressTestResult())) {
			risks.add("Fails the rate-rise stress test - monthly costs would exceed income if rates rose "
					+ (int) AffordabilityCalculator.STRESS_DELTA_PCT + " points");
		} else if ("TIGHT".equals(f.stressTestResult())) {
			risks.add("Thin buffer (" + f.monthlyBufferAfterStress()
					+ "/month) after a rate-rise stress test");
		}
		if (risks.isEmpty()) {
			risks.add("No major risk indicators from the information provided so far");
		}
		return risks;
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
