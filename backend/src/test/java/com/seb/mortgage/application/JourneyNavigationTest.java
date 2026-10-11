package com.seb.mortgage.application;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Issue #16: moving back and forth through the journey. The path follows the answers,
 * a step opens once every step before it is done, and answers from a branch that is no
 * longer taken are dropped.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class JourneyNavigationTest {

	@Autowired ApplicationService service;

	private UUID id;

	@BeforeEach
	void newApplication() {
		id = service.create().getId();
	}

	private LoanApplication advance(String stepId, Map<String, Object> answers) {
		return service.submitStep(id, stepId, answers, true);
	}

	private ApplicationService.Journey journey() {
		return service.journey(service.get(id));
	}

	private void completeUpToApplicants() {
		advance("welcome", Map.of());
		advance("purpose", Map.of("loanPurpose", "PURCHASE"));
		advance("property", Map.of("propertyAddress", "Main St 1", "propertyType", "HOUSE", "purchasePrice", 300_000));
	}

	private void completeFromIncomeToEnd() {
		advance("income", Map.of("employmentType", "EMPLOYED", "monthlyGrossIncome", 5_000, "monthlyDebtPayments", 0));
		advance("loan", Map.of("downPayment", 60_000, "loanAmount", 240_000, "termYears", 25, "rateType", "VARIABLE"));
		advance("review", Map.of());
	}

	@Test
	void freshApplicationOnlyOpensTheFirstStep() {
		var journey = journey();

		assertThat(journey.path()).containsExactly("welcome", "purpose", "property", "applicants", "income", "loan", "review");
		assertThat(journey.unlockedSteps()).containsExactly("welcome");
	}

	@Test
	void canGoBackToADoneStepButNotSkipAhead() {
		completeUpToApplicants();
		assertThat(service.get(id).getCurrentStepId()).isEqualTo("applicants");
		assertThat(journey().unlockedSteps()).containsExactly("welcome", "purpose", "property", "applicants");

		assertThat(service.goToStep(id, "purpose").getCurrentStepId()).isEqualTo("purpose");
		assertThat(service.get(id).getAnswers()).containsEntry("purchasePrice", 300_000);
		assertThatThrownBy(() -> service.goToStep(id, "loan")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void savingWithoutAdvancingStaysOnTheStep() {
		completeUpToApplicants();
		service.goToStep(id, "property");

		var saved = service.submitStep(id, "property", Map.of("purchasePrice", 320_000), false);

		assertThat(saved.getCurrentStepId()).isEqualTo("property");
		assertThat(saved.getAnswers()).containsEntry("purchasePrice", 320_000);
	}

	@Test
	void changingABranchDropsTheOldBranchAndReopensTheNewOne() {
		completeUpToApplicants();
		advance("applicants", Map.of("hasCoApplicant", true));
		assertThat(service.get(id).getCurrentStepId()).isEqualTo("co-applicant");
		advance("co-applicant", Map.of("coApplicantName", "Sam", "coApplicantMonthlyIncome", 3_000));
		completeFromIncomeToEnd();
		assertThat(service.get(id).isComplete()).isTrue();

		// Back to "who is applying", now without a co-applicant: their answers go, the
		// rest of the journey stays done.
		service.goToStep(id, "applicants");
		var single = advance("applicants", Map.of("hasCoApplicant", false));
		assertThat(single.getAnswers()).doesNotContainKeys("coApplicantName", "coApplicantMonthlyIncome");
		assertThat(journey().path()).doesNotContain("co-applicant");
		assertThat(single.isComplete()).isTrue();

		// And back again: the co-applicant step is new and undone, so everything after it
		// is locked until it is filled in.
		service.goToStep(id, "applicants");
		var couple = advance("applicants", Map.of("hasCoApplicant", true));
		assertThat(couple.isComplete()).isFalse();
		assertThat(couple.getCurrentStepId()).isEqualTo("co-applicant");
		assertThat(journey().unlockedSteps()).endsWith("applicants", "co-applicant").doesNotContain("loan");
	}
}
