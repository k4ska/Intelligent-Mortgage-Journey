package com.seb.mortgage.mockdata;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standalone "what would my payment be" calculator - pure math, no persistence, no agent
 * calls, usable before a {@code LoanApplication} exists. Wraps the same
 * {@link AffordabilityCalculator} the full journey uses internally via
 * {@code LoanProfileAssembler}, so a quick estimate here and the real profile later agree.
 */
@RestController
@RequestMapping("/api/calculator")
public class CalculatorController {

	private final AffordabilityCalculator calculator;
	private final RateService rateService;

	public CalculatorController(AffordabilityCalculator calculator, RateService rateService) {
		this.calculator = calculator;
		this.rateService = rateService;
	}

	@PostMapping("/estimate")
	public EstimateResponse estimate(@RequestBody EstimateRequest request) {
		double price = nonNegative(request.propertyPrice());
		double downPayment = nonNegative(request.downPayment());
		double loanAmount = Math.max(price - downPayment, 0);
		Double requestedRate = request.annualRatePct();
		Integer requestedTerm = request.termYears();
		double annualRatePct = requestedRate == null ? rateService.rateFor(null, null) : requestedRate;
		int termYears = requestedTerm == null ? 25 : requestedTerm;

		double monthlyPayment = calculator.monthlyPayment(loanAmount, annualRatePct, termYears);
		double ltv = calculator.loanToValue(loanAmount, price);

		return new EstimateResponse(Math.round(loanAmount), Math.round(monthlyPayment), ltv, annualRatePct, termYears);
	}

	/**
	 * "How much can I borrow?" - the largest loan the journey's affordability checks would
	 * accept for this income, debts and (optional) down payment.
	 */
	@PostMapping("/max-loan")
	public MaxLoanResponse maxLoan(@RequestBody MaxLoanRequest request) {
		double income = nonNegative(request.monthlyIncome()) + nonNegative(request.coApplicantMonthlyIncome());
		double debts = nonNegative(request.monthlyDebtPayments());
		Double downPayment = request.downPayment() == null || request.downPayment() <= 0 ? null : request.downPayment();
		Double requestedRate = request.annualRatePct();
		Integer requestedTerm = request.termYears();
		double annualRatePct = requestedRate == null ? rateService.rateFor(null, null) : requestedRate;
		int termYears = requestedTerm == null ? 25 : requestedTerm;

		AffordabilityCalculator.MaxLoan max = calculator.maxLoan(income, debts, downPayment, annualRatePct, termYears);
		double maxLoanAmount = max.amount();
		Double maxPropertyPrice = downPayment == null ? null : (double) Math.round(maxLoanAmount + downPayment);

		return new MaxLoanResponse(maxLoanAmount, maxPropertyPrice,
				Math.round(calculator.monthlyPayment(maxLoanAmount, annualRatePct, termYears)),
				max.limitedBy(), annualRatePct, AffordabilityCalculator.stressedRate(annualRatePct), termYears,
				AffordabilityCalculator.MAX_DTI_PCT, AffordabilityCalculator.TYPICAL_MAX_LTV_PCT);
	}

	private static double nonNegative(Double v) {
		return v == null || v < 0 ? 0 : v;
	}

	public record EstimateRequest(Double propertyPrice, Double downPayment, Double annualRatePct, Integer termYears) {}

	public record EstimateResponse(
			double loanAmount, double monthlyPayment, double ltv, double annualRatePct, int termYears) {}

	public record MaxLoanRequest(Double monthlyIncome, Double coApplicantMonthlyIncome, Double monthlyDebtPayments,
			Double downPayment, Double annualRatePct, Integer termYears) {}

	/**
	 * Incomes are monthly net (after tax). {@code maxPropertyPrice} is null when no down
	 * payment was given; {@code maxDtiPct} applies at {@code stressRatePct}.
	 */
	public record MaxLoanResponse(double maxLoanAmount, Double maxPropertyPrice, double monthlyPayment,
			AffordabilityCalculator.LoanLimit limitedBy, double annualRatePct, double stressRatePct, int termYears,
			double maxDtiPct, double maxLtvPct) {}
}
