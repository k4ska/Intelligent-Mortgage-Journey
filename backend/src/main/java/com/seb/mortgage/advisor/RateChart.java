package com.seb.mortgage.advisor;

import java.util.List;

import com.seb.mortgage.bank.OfferCalculator;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfileAssembler;

/**
 * The chart of how Euribor changes the user's monthly payment (#130, vision step 6): the
 * payment for their loan at today's Euribor and at a few levels around it, next to the
 * payment the stress test already checked. All figures come from the calculator, none
 * from the model ({@code docs/architecture.md} §1, rule 1).
 *
 * @param stressedPayment the payment at the stress-test rate, which the affordability check passed
 */
public record RateChart(double loanAmount, int termYears, double marginPct, double stressRatePct,
		double stressedPayment, List<Point> points) {

	public static final String TYPE = "rate_payment";

	/** Euribor in points from today: one lower, today's, and one, two and three higher. */
	static final List<Integer> STEPS = List.of(-1, 0, 1, 2, 3);

	/** @param today whether this is today's Euribor */
	public record Point(double euriborPct, double ratePct, double monthlyPayment, boolean today) {}

	static RateChart of(LoanProfileAssembler.Checked checked, AffordabilityCalculator calculator) {
		LoanProfileAssembler.Terms terms = checked.terms();
		return of(checked.profile().loanRequest().amount(), checked.profile().loanRequest().termYears(),
				terms.variableRatePct(), terms.marginPct(), terms.ratePct(), calculator);
	}

	/** The chart for a bank's offer (#160): its loan, term and margin instead of the advisor's estimate. */
	static RateChart of(OfferCalculator.Offer offer, AffordabilityCalculator calculator) {
		return of(offer.amount(), offer.termYears(), offer.euriborPct(), offer.marginPct(), offer.ratePct(), calculator);
	}

	private static RateChart of(double loan, int termYears, double euriborToday, double marginPct, double ratePct,
			AffordabilityCalculator calculator) {
		List<Point> points = STEPS.stream()
				.map(step -> {
					// Euribor has been below zero, but a chart of a negative rate would only confuse.
					double euribor = round2(Math.max(0, euriborToday + step));
					double rate = round2(euribor + marginPct);
					return new Point(euribor, rate, round2(calculator.monthlyPayment(loan, rate, termYears)), step == 0);
				})
				.toList();
		double stressRate = AffordabilityCalculator.stressedRate(ratePct);
		return new RateChart(loan, termYears, marginPct, stressRate,
				round2(calculator.monthlyPayment(loan, stressRate, termYears)), points);
	}

	ChartView view() {
		return new ChartView(TYPE, "Your monthly payment if Euribor changes", this);
	}

	/** The view of the chart for the offer the user accepted from this bank. */
	ChartView view(String bank) {
		return new ChartView(TYPE, "Your monthly payment with " + bank + "'s offer if Euribor changes", this);
	}

	private static double round2(double value) {
		return Math.round(value * 100) / 100.0;
	}
}
