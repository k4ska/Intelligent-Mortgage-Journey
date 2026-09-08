package com.seb.mortgage.flow;

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
		assertThat(next).map(FlowDefinition.Step::id).contains("self-employed");
	}

	@Test
	void fixedRateBranchIsTaken() {
		var next = flowService.nextStep("loan", Map.of("rateType", "FIXED"));
		assertThat(next).map(FlowDefinition.Step::id).contains("fixed-details");
	}

	@Test
	void lastStepHasNoNext() {
		var next = flowService.nextStep("review", Map.of());
		assertThat(next).isEmpty();
	}
}
