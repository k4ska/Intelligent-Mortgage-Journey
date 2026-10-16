package com.seb.mortgage.bank;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * The five banks and their rules. The one catalogue for both sides: the advisor reads ids
 * and names for the bank picker, the bank agents read the rules. All rule values are
 * prototype placeholders, not the banks' real terms.
 */
@Service
public class BankCatalogService {

	/**
	 * @param tone     how the bank writes, for the bank agent's prompt
	 * @param greeting the canned opening line used when there is no LLM, written in that tone
	 */
	public record Bank(String id, String name, String tone, String greeting, double baseMarginPct,
			double lowestMarginPct, double maxLtvPct, double minDownPaymentPct) {}

	private static final List<Bank> BANKS = List.of(
			new Bank("seb", "SEB", "clear and reassuring",
					"Thank you for your application. We have received it and will guide you through each step.",
					1.65, 1.35, 85, 15),
			new Bank("swedbank", "Swedbank", "friendly and practical",
					"Hi, and thanks for choosing Swedbank! Your application is with us.",
					1.70, 1.40, 85, 15),
			new Bank("lhv", "LHV", "direct and concise",
					"Application received.",
					1.75, 1.45, 85, 15),
			new Bank("luminor", "Luminor", "formal and helpful",
					"Dear applicant, we hereby confirm receipt of your mortgage application.",
					1.80, 1.50, 85, 15),
			new Bank("coop", "Coop Pank", "warm and straightforward",
					"Thank you for reaching out to Coop Pank. We're happy to look at your application.",
					1.85, 1.55, 90, 10));

	public List<Bank> banks() {
		return BANKS;
	}

	/** @throws UnknownBankException if no bank has this id */
	public Bank find(String id) {
		return BANKS.stream().filter(bank -> bank.id().equals(id)).findFirst()
				.orElseThrow(() -> new UnknownBankException(id));
	}

	public static class UnknownBankException extends IllegalArgumentException {
		public UnknownBankException(String id) {
			super("Unknown bank: " + id);
		}
	}
}
