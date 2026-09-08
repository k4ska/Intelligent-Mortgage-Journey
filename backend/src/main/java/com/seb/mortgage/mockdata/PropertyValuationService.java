package com.seb.mortgage.mockdata;

import org.springframework.stereotype.Service;

/**
 * Mock property valuation. Returns a deterministic pseudo-estimate derived from the
 * address string so the same address always yields the same number in a demo.
 */
@Service
public class PropertyValuationService {

	public record Valuation(double estimatedValue, double confidencePct, String method) {}

	public Valuation estimate(String address, Double declaredPrice) {
		double base = declaredPrice != null && declaredPrice > 0 ? declaredPrice : 250_000;
		int hash = address == null ? 0 : Math.abs(address.trim().toLowerCase().hashCode());
		double variance = (hash % 21 - 10) / 100.0; // -10%..+10%
		double estimate = Math.round(base * (1 + variance) / 1000.0) * 1000.0;
		return new Valuation(estimate, 72, "Automated comparable model (mock)");
	}
}
