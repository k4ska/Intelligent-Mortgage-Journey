package com.seb.mortgage.bank;

import java.util.List;

import org.springframework.stereotype.Service;

@Service("bankSideCatalogService")
public class BankCatalogService {
	public record Bank(String id, String name, String tone, double baseMarginPct,
			double lowestMarginPct, double maxLtvPct, double minDownPaymentPct) {}

	private static final List<Bank> BANKS = List.of(
			new Bank("seb", "SEB", "clear and reassuring", 1.65, 1.35, 85, 15),
			new Bank("swedbank", "Swedbank", "friendly and practical", 1.70, 1.40, 85, 15),
			new Bank("lhv", "LHV", "direct and concise", 1.75, 1.45, 85, 15),
			new Bank("luminor", "Luminor", "formal and helpful", 1.80, 1.50, 85, 15),
			new Bank("coop", "Coop Pank", "warm and straightforward", 1.85, 1.55, 90, 10));

	public List<Bank> banks() { return BANKS; }

	public Bank find(String id) {
		return BANKS.stream().filter(bank -> bank.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown bank: " + id));
	}
}