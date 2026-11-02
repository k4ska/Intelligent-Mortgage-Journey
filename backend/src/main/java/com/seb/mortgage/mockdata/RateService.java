package com.seb.mortgage.mockdata;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock interest-rate table. Static sample data; not live market rates.
 */
@Service
public class RateService {

	/**
	 * The 6-month Euribor every variable rate is built on (rate = Euribor + the bank's margin).
	 * Close to the market level in 2026 (about 2%), so the offers look like real Estonian ones (#116).
	 */
	public static final double EURIBOR_6M_PCT = 2.1;

	public record Rate(String rateType, int fixedYears, double annualRatePct, String note) {}

	private static final List<Rate> RATES = List.of(
			new Rate("VARIABLE", 0, EURIBOR_6M_PCT, "6-month Euribor; the payment can move every 6 months"),
			new Rate("FIXED", 2, 2.2, "Fixed for 2 years, then reverts to variable"),
			new Rate("FIXED", 5, 2.4, "Fixed for 5 years"),
			new Rate("FIXED", 10, 2.7, "Fixed for 10 years; highest certainty")
	);

	public List<Rate> all() {
		return RATES;
	}

	/** Best-guess rate for a given rate type / fixed period. Falls back to the variable rate. */
	public double rateFor(String rateType, Integer fixedYears) {
		return RATES.stream()
				.filter(r -> r.rateType().equalsIgnoreCase(rateType == null ? "VARIABLE" : rateType))
				.filter(r -> fixedYears == null || r.fixedYears() == fixedYears)
				.mapToDouble(rate -> rate.annualRatePct())
				.findFirst()
				.orElse(EURIBOR_6M_PCT);
	}
}
