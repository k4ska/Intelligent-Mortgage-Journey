package com.seb.mortgage.flow;

import com.seb.mortgage.agent.Explanations;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowServiceTest {

	private final FlowService flowService =
			new FlowService(new ObjectMapper(), new DefaultResourceLoader());

	@Test
	void loadsDefinition() {
		FlowDefinition def = flowService.definition();
		assertThat(def.steps()).isNotEmpty();
		assertThat(flowService.firstStep().id()).isEqualTo("welcome");
	}

	@Test
	void everyFlowFieldHasGlossaryCoverage() {
		for (FlowDefinition.Step step : flowService.definition().steps()) {
			for (FlowDefinition.Field field : step.fields()) {
				assertThat(field.helpKey())
						.as("field '%s' in step '%s' should have a helpKey", field.key(), step.id())
						.isNotBlank();

				assertThat(Explanations.lookup(field.helpKey()))
						.as("helpKey '%s' for field '%s' in step '%s' should exist in Explanations",
								field.helpKey(), field.key(), step.id())
						.isNotNull();
			}
		}
	}

	@Test
	void everyRelatedGlossaryTopicExists() {
		Explanations.BY_KEY.forEach((key, entry) -> {
			for (String relatedKey : entry.related()) {
				assertThat(Explanations.lookup(relatedKey))
						.as("glossary entry '%s' references missing related topic '%s'", key, relatedKey)
						.isNotNull();
			}
		});
	}

	@Test
	void coApplicantBranchIsTakenWhenFlagged() {
		var next = flowService.nextStep("applicants", Map.of("hasCoApplicant", true));
		assertThat(next).isPresent();
		assertThat(next.get().id()).isEqualTo("co-applicant");
	}

	@Test
	void coApplicantBranchIsSkippedByDefault() {
		var next = flowService.nextStep("applicants", Map.of("hasCoApplicant", false));
		assertThat(next).isPresent();
		assertThat(next.get().id()).isEqualTo("income");
	}

	@Test
	void selfEmployedBranchIsTaken() {
		var next = flowService.nextStep("income", Map.of("employmentType", "SELF_EMPLOYED"));
		assertThat(next).isPresent();
		assertThat(next.orElseThrow().id()).isEqualTo("self-employed");
	}

	/** Issue #31: self-employed applicants give last year's net profit, not a monthly salary. */
	@Test
	void monthlyNetIncomeIsHiddenForSelfEmployed() {
		FlowDefinition.Field income = flowService.step("income").orElseThrow().fields().stream()
				.filter(f -> f.key().equals("monthlyNetIncome")).findFirst().orElseThrow();

		assertThat(income.visible(Map.of())).isTrue();
		assertThat(income.visible(Map.of("employmentType", "EMPLOYED"))).isTrue();
		assertThat(income.visible(Map.of("employmentType", "SELF_EMPLOYED"))).isFalse();
	}

	@Test
	void fixedRateBranchIsTaken() {
		var next = flowService.nextStep("loan", Map.of("rateType", "FIXED"));
		assertThat(next).isPresent();
		assertThat(next.orElseThrow().id()).isEqualTo("fixed-details");
	}

	@Test
	void lastStepHasNoNext() {
		var next = flowService.nextStep("review", Map.of());
		assertThat(next).isEmpty();
	}
}