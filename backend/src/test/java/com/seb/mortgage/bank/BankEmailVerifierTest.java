package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class BankEmailVerifierTest {

	private final BankCatalogService.Bank bank = new BankCatalogService().find("seb");
	private final BankEmailVerifier verifier = new BankEmailVerifier(new ObjectMapper());
	private final OfferCalculator.Offer offer = new OfferCalculator.Offer(
			160000, 25, 4.35, 1.65, 6.0, 1031.22,
			List.of("Valuation confirms the property value"), LocalDate.of(2026, 11, 30));
	private final OfferCalculator.Decision decision = OfferCalculator.Decision.offer(offer);

	@Test
	void rewritesAnOfferThatContainsTheWrongNumber() {
		BankAgent.Draft draft = new BankAgent.Draft("offer", "Offer",
				"We offer €999999 over 25 years at 6.0%. "
						+ "Euribor 4.35% plus margin 1.65%. Payment €1031.22. Valid until 2026-11-30.");

		BankAgent.Draft checked = verifier.verify(draft, decision, bank);

		assertThat(checked.body()).contains("€160000").doesNotContain("€999999");
		assertThat(checked.type()).isEqualTo("offer");
	}

	@Test
	void leavesAMatchingOfferUntouched() {
		BankAgent.Draft draft = new BankAgent.Draft("offer", "Offer",
				"We offer €160000 over 25 years at 6.0%. "
						+ "Euribor 4.35% plus margin 1.65%. Payment €1031.22. Valid until 2026-11-30.");

		assertThat(verifier.verify(draft, decision, bank)).isSameAs(draft);
	}
}
