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

	/** Above this debt-to-income (all debts incl. the mortgage, % of income) a profile is flagged. */
	public static final double MAX_DTI_PCT = 40.0;

	/** Money a month left after debts and the stressed payment for the stress test to PASS. */
	public static final double MIN_STRESS_BUFFER = 300.0;

	/**
	 * Loan-to-value a typical lender allows. The mock banks range from 80% to 95%; the
	 * quick max-loan estimate uses this middle value so it doesn't promise the most generous one.
	 */
	public static final double TYPICAL_MAX_LTV_PCT = 85.0;

	/** Which rule capped {@link #maxLoan}. */
	public enum LoanLimit { INCOME, STRESS, DOWN_PAYMENT }

	public record MaxLoan(double amount, LoanLimit limitedBy) {}

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

	/** Inverse of {@link #monthlyPayment}: the principal a given monthly payment pays off. */
	public double principalFor(double monthlyPayment, double annualRatePct, int termYears) {
		if (monthlyPayment <= 0 || termYears <= 0) {
			return 0;
		}
		int months = termYears * 12;
		double monthlyRate = annualRatePct / 100.0 / 12.0;
		if (monthlyRate == 0) {
			return monthlyPayment * months;
		}
		return monthlyPayment * (1 - Math.pow(1 + monthlyRate, -months)) / monthlyRate;
	}

	/**
	 * The largest loan the journey's own checks would accept: debt-to-income within
	 * {@link #MAX_DTI_PCT}, a stress-test buffer of at least {@link #MIN_STRESS_BUFFER}, and,
	 * when a down payment is given, loan-to-value within {@link #TYPICAL_MAX_LTV_PCT}.
	 * Floored to whole euros so a loan at the maximum never lands just over a limit.
	 * The amount is 0 when the income can't carry any loan.
	 */
	public MaxLoan maxLoan(double monthlyIncome, double monthlyDebts, Double downPayment,
			double annualRatePct, int termYears) {
		double byIncome = principalFor(MAX_DTI_PCT / 100.0 * monthlyIncome - monthlyDebts, annualRatePct, termYears);
		double byStress = principalFor(monthlyIncome - monthlyDebts - MIN_STRESS_BUFFER,
				annualRatePct + STRESS_DELTA_PCT, termYears);

		double amount = byIncome;
		LoanLimit limitedBy = LoanLimit.INCOME;
		if (byStress < amount) {
			amount = byStress;
			limitedBy = LoanLimit.STRESS;
		}
		if (downPayment != null && downPayment > 0) {
			// loan / (loan + down payment) <= LTV, kept in whole percentages so 15 000 at 85% is exactly 85 000.
			double byDownPayment = downPayment * TYPICAL_MAX_LTV_PCT / (100 - TYPICAL_MAX_LTV_PCT);
			if (byDownPayment < amount) {
				amount = byDownPayment;
				limitedBy = LoanLimit.DOWN_PAYMENT;
			}
		}
		return new MaxLoan(Math.max(0, Math.floor(amount)), limitedBy);
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
