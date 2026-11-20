package com.seb.mortgage.advisor;

import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.LoanProfileAssembler;

/**
 * What the home costs in all (#160): the down payment, the loan, and the interest paid on it
 * over the whole term at today's rate. All figures come from the calculator, none from the
 * model ({@code docs/architecture.md} §1, rule 1).
 *
 * @param totalInterest the monthly payment times the number of months, less the loan
 */
public record CostBreakdown(double price, double downPayment, double loanAmount, int termYears, double ratePct,
		double monthlyPayment, double totalInterest) {

	public static final String TYPE = "cost_breakdown";

	static CostBreakdown of(LoanProfileAssembler.Checked checked, AffordabilityCalculator calculator) {
		LoanProfile profile = checked.profile();
		double loan = profile.loanRequest().amount();
		int termYears = profile.loanRequest().termYears();
		double rate = checked.terms().ratePct();
		double monthly = round2(calculator.monthlyPayment(loan, rate, termYears));
		return new CostBreakdown(round2(profile.property().purchasePrice()), round2(profile.financials().downPayment()),
				round2(loan), termYears, round2(rate), monthly, round2(monthly * termYears * 12 - loan));
	}

	ChartView view() {
		return new ChartView(TYPE, "What the home costs over the whole loan", this);
	}

	private static double round2(double value) {
		return Math.round(value * 100) / 100.0;
	}
}
