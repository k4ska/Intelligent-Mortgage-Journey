package com.seb.mortgage.mockdata;

import com.seb.mortgage.profile.LoanProfile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock catalogue of banks and a fake offer generator. This is the "conceptually across
 * multiple banks" piece: one standardized {@link LoanProfile} fans out to several offers.
 */
@Service
public class BankCatalogService {

	public record Bank(String id, String name, double rateAdjustmentPct, double maxLtv, String personality) {}

	public record Offer(
			String bankId,
			String bankName,
			boolean preApproved,
			double offeredRatePct,
			double maxLoanAmount,
			double estimatedMonthlyPayment,
			List<String> conditions
	) {}

	private static final List<Bank> BANKS = List.of(
			new Bank("seb", "SEB", -0.05, 85, "Clear and reassuring"),
			new Bank("swedbank", "Swedbank", 0.10, 85, "Friendly and practical"),
			new Bank("lhv", "LHV", 0.00, 85, "Direct and concise"),
			new Bank("luminor", "Luminor", 0.15, 85, "Formal and helpful"),
			new Bank("coop", "Coop Pank", 0.20, 90, "Warm and straightforward")
	);

	private final AffordabilityCalculator calculator;

	public BankCatalogService(AffordabilityCalculator calculator) {
		this.calculator = calculator;
	}

	public List<Bank> banks() {
		return BANKS;
	}

	public List<Offer> generateOffers(LoanProfile profile) {
		double baseRate = profile.financials() == null ? 4.35
				: Math.max(0.5, impliedBaseRate(profile));
		return BANKS.stream().map(bank -> {
			double rate = round2(baseRate + bank.rateAdjustmentPct());
			boolean ltvOk = profile.financials() != null && profile.financials().ltv() <= bank.maxLtv();
			double amount = profile.loanRequest() == null ? 0 : profile.loanRequest().amount();
			int term = profile.loanRequest() == null ? 25 : profile.loanRequest().termYears();
			double payment = calculator.monthlyPayment(amount, rate, term);
			return new Offer(
					bank.id(),
					bank.name(),
					ltvOk,
					rate,
					amount * (ltvOk ? 1.0 : 0.9),
					round2(payment),
					ltvOk
							? List.of("Subject to full income verification", "Valuation survey required")
							: List.of("Loan-to-value above this lender's limit of " + bank.maxLtv() + "%",
									"A larger down payment would unlock this offer")
			);
		}).toList();
	}

	private double impliedBaseRate(LoanProfile profile) {
		double payment = profile.financials().estimatedMonthlyPayment();
		double amount = profile.loanRequest().amount();
		if (payment <= 0 || amount <= 0) {
			return 4.35;
		}
		// Rough inversion is unnecessary for a mock; keep the estimated rate stable.
		return 4.35;
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
