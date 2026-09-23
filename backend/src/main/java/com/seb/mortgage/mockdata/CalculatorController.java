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
		double annualRatePct = request.annualRatePct() != null ? request.annualRatePct() : rateService.rateFor(null, null);
		int termYears = request.termYears() != null ? request.termYears() : 25;

		double monthlyPayment = calculator.monthlyPayment(loanAmount, annualRatePct, termYears);
		double ltv = calculator.loanToValue(loanAmount, price);

		return new EstimateResponse(Math.round(loanAmount), Math.round(monthlyPayment), ltv, annualRatePct, termYears);
	}

	private static double nonNegative(Double v) {
		return v == null || v < 0 ? 0 : v;
	}

	public record EstimateRequest(Double propertyPrice, Double downPayment, Double annualRatePct, Integer termYears) {}

	public record EstimateResponse(
			double loanAmount, double monthlyPayment, double ltv, double annualRatePct, int termYears) {}
}
