package com.seb.mortgage.agent;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Issue #17: "What's this?" shows the built-in definition without calling the LLM, and
 * that definition comes from {@link Explanations}, not a copy in the frontend.
 */
class ExplanationControllerTest {

	private final ExplanationController controller = new ExplanationController();

	@Test
	void servesEveryBuiltInDefinition() {
		assertThat(controller.all()).isEqualTo(Explanations.BY_KEY);
	}

	@Test
	void servesOneDefinitionByKey() {
		var response = controller.one("Loan-To-Value");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(Explanations.lookup("loan-to-value"));
	}

	@Test
	void unknownKeyIsNotFound() {
		assertThat(controller.one("no-such-term").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/** Issue #39: the landing hub's topic cards show these as static content, chips included. */
	@ParameterizedTest
	@ValueSource(strings = { "what-is-a-loan", "top-mortgage-mistakes" })
	void hubTopicsAndTheirRelatedChipsAllHaveDefinitions(String key) {
		Explanations.Entry entry = controller.all().get(key);

		assertThat(entry).isNotNull();
		assertThat(entry.related()).isNotEmpty().allSatisfy(related ->
				assertThat(controller.all()).containsKey(related));
	}
}
