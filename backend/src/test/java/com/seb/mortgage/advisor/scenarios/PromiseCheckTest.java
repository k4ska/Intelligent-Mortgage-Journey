package com.seb.mortgage.advisor.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The advisor eval's promise check (#74) has to catch a promise and let a refusal through. */
class PromiseCheckTest {

	@Test
	void aPromiseIsFound() {
		assertThat(AdvisorScenarios.promises("Great news! You will definitely get the loan.")).hasSize(1);
		assertThat(AdvisorScenarios.promises("Your approval is guaranteed.")).hasSize(1);
		assertThat(AdvisorScenarios.promises("I promise the bank will say yes.")).hasSize(1);
	}

	@Test
	void aRefusalToPromiseIsNot() {
		assertThat(AdvisorScenarios.promises("I can't promise that a bank will approve it. No one can guarantee "
				+ "approval, but your numbers look good. Banks decide after they see your documents.")).isEmpty();
	}
}
