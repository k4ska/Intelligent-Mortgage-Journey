package com.seb.mortgage.mockdata;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock interest-rate table. Static sample data; not live market rates.
 */
@Service
public class RateService {

	public record Rate(String rateType, int fixedYears, double annualRatePct, String note) {}

	private static final List<Rate> RATES = List.of(
			new Rate("VARIABLE", 0, 4.35, "Tracks the reference rate; payment can move every period"),
			new Rate("FIXED", 2, 4.10, "Fixed for 2 years, then reverts to variable"),
			new Rate("FIXED", 5, 4.25, "Fixed for 5 years"),
			new Rate("FIXED", 10, 4.60, "Fixed for 10 years; highest certainty")
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
				.orElse(4.35);
	}
}
