package com.seb.mortgage.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.seb.mortgage.mockdata.AffordabilityCalculator;

/** The glossary behind explainTerm (#66). */
class ExplanationsTest {

	@ParameterizedTest
	@CsvSource({
			"loan-to-value, loan-to-value",
			"Loan to value, loan-to-value",
			"LTV, loan-to-value",
			"What is the LTV?, loan-to-value",
			"Euribor, euribor",
			"bank margin, margin",
			"stress tests, stress-test",
			"deposit, down-payment",
			"price per m², price-per-square-metre",
			"How much can I borrow, affordability",
			"notary, purchase-agreement",
			"credit card limits, credit-limits"
	})
	void findsATopicHoweverItIsWritten(String topic, String key) {
		assertThat(Explanations.find(topic)).map(Explanations.Found::key).contains(key);
	}

	@Test
	void anUnknownTopicIsNotFound() {
		assertThat(Explanations.find("balloon payment")).isEmpty();
		assertThat(Explanations.find("  ")).isEmpty();
		assertThat(Explanations.find(null)).isEqualTo(Optional.empty());
	}

	@Test
	void everyRelatedTopicIsInTheGlossary() {
		Explanations.BY_KEY.forEach((key, entry) ->
				assertThat(Explanations.topics()).as("related topics of " + key).containsAll(entry.related()));
	}

	/** The advisor now talks about these (affordability, property, documents), so they need entries. */
	@Test
	void coversTheTermsTheAdvisorUses() {
		assertThat(Explanations.topics()).contains("euribor", "margin", "loan-to-value", "stress-test",
				"debt-to-income", "affordability", "household-costs", "credit-limits", "down-payment", "loan-term",
				"price-per-square-metre", "property-type", "new-build", "valuation", "purchase-agreement",
				"down-payment-proof", "proof-of-income");
	}

	/** The glossary states our rules in words; they must say what the calculator does. */
	@Test
	void theRulesInTheTextMatchTheCalculator() {
		assertThat(Explanations.lookup("affordability").text())
				.contains("at most half of your net income")
				.contains("at least " + (int) AffordabilityCalculator.MIN_STRESS_BUFFER + " euros")
				.contains("at least " + (100 - (int) AffordabilityCalculator.TYPICAL_MAX_LTV_PCT) + "% of the price")
				.contains("at most " + (int) AffordabilityCalculator.TYPICAL_MAX_LTV_PCT + "%");
		assertThat(Explanations.lookup("stress-test").text())
				.contains("plus " + (int) AffordabilityCalculator.STRESS_DELTA_PCT + " percentage points")
				.contains("at least " + (int) AffordabilityCalculator.STRESS_RATE_FLOOR_PCT + "%");
		assertThat(Explanations.lookup("credit-limits").text())
				.contains("(here " + (int) AffordabilityCalculator.CREDIT_LIMIT_MONTHLY_PCT + "%)");
		assertThat(AffordabilityCalculator.MAX_DTI_PCT).isEqualTo(50.0);
	}
}
