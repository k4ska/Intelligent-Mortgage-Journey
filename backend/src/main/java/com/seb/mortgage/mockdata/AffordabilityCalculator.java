package com.seb.mortgage.mockdata;

import org.springframework.stereotype.Service;

/**
 * Simplified affordability maths. Not regulatory-accurate (by design, per the brief) but
 * good enough to make the agent's feedback concrete.
 */
@Service
public class AffordabilityCalculator {

	/** Percentage points added to the rate for the stress test. */
	public static final double STRESS_DELTA_PCT = 3.0;

	/** Standard annuity payment for a fully amortising loan. */
	public double monthlyPayment(double principal, double annualRatePct, int termYears) {
		if (principal <= 0 || termYears <= 0) {
			return 0;
		}
		int months = termYears * 12;
		double monthlyRate = annualRatePct / 100.0 / 12.0;
		if (monthlyRate == 0) {
			return principal / months;
		}
		double factor = Math.pow(1 + monthlyRate, -months);
		return principal * monthlyRate / (1 - factor);
	}

	public double stressedMonthlyPayment(double principal, double annualRatePct, int termYears) {
		return monthlyPayment(principal, annualRatePct + STRESS_DELTA_PCT, termYears);
	}

	public double loanToValue(double loanAmount, double propertyValue) {
		return propertyValue <= 0 ? 0 : round1(loanAmount / propertyValue * 100.0);
	}

	public double debtToIncome(double monthlyDebtPayments, double monthlyGrossIncome) {
		return monthlyGrossIncome <= 0 ? 0 : round1(monthlyDebtPayments / monthlyGrossIncome * 100.0);
	}

	private static double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}
}
