package com.seb.mortgage.profile;

import java.util.List;

/**
 * Contract 3 of 3 (see README): the standardized loan profile.
 *
 * <p>Assembled from a completed (or in-progress) application plus derived financials.
 * This is the single structured object that feeds the mock multi-bank comparison screen,
 * and conceptually the object that could be shared across banks.
 */
public record LoanProfile(
		List<Applicant> applicants,
		Property property,
		LoanRequest loanRequest,
		Financials financials,
		List<String> riskIndicators,
		List<String> documents
) {

	public record Applicant(
			String employment,
			double monthlyGrossIncome,
			double monthlyDebtPayments
	) {}

	public record Property(
			String type,
			String location,
			double purchasePrice,
			double estimatedValue
	) {}

	public record LoanRequest(
			double amount,
			int termYears,
			String rateType,
			String purpose
	) {}

	public record Financials(
			double downPayment,
			/** Loan-to-value, %. */
			double ltv,
			/** Debt-to-income, %. */
			double dti,
			double estimatedMonthlyPayment,
			/** Monthly payment if rates rose by the stress-test delta. */
			double stressedMonthlyPayment,
			double monthlyBufferAfterStress,
			String stressTestResult
	) {}
}
