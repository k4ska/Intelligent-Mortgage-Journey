package com.seb.mortgage.profile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.seb.mortgage.application.LoanApplication;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.DocumentChecklistService;
import com.seb.mortgage.mockdata.PropertyValuationService;
import com.seb.mortgage.mockdata.RateService;

/**
 * The agents' view of an application must never contain the defaults the full profile
 * fills in: a figure only appears once every input it depends on is a real answer.
 */
class ProfileFactsTest {

	private final LoanProfileAssembler assembler = new LoanProfileAssembler(new AffordabilityCalculator(),
			new PropertyValuationService(), new RateService(), new DocumentChecklistService());

	private static final Map<String, Object> LOAN_STEP = Map.of(
			"downPayment", 25_000, "loanAmount", 475_000, "termYears", 25, "rateType", "VARIABLE");
	private static final Map<String, Object> INCOME_STEP = Map.of(
			"employmentType", "EMPLOYED", "monthlyGrossIncome", 4_000, "monthlyDebtPayments", 300);

	private ProfileFacts factsFor(Map<String, Object> answers) {
		LoanApplication application = new LoanApplication("purpose");
		application.putAnswers(answers);
		return assembler.facts(application);
	}

	private static List<String> names(List<ProfileFacts.Figure> figures) {
		return figures.stream().map(ProfileFacts.Figure::name).toList();
	}

	@Test
	void freshApplicationHasNoAnswersAndNoFigures() {
		ProfileFacts facts = factsFor(Map.of());

		assertThat(facts.answers()).isEmpty();
		assertThat(facts.figures()).isEmpty();
		assertThat(facts.notAnsweredYet()).contains("purchasePrice", "downPayment", "monthlyGrossIncome");
	}

	@Test
	void priceAloneGivesOnlyTheValuationNotLoanToValue() {
		ProfileFacts facts = factsFor(Map.of("purchasePrice", 500_000, "propertyAddress", "Main St 1"));

		assertThat(names(facts.figures())).containsExactly("estimatedPropertyValue");
		assertThat(facts.notAnsweredYet()).contains("downPayment").doesNotContain("purchasePrice");
	}

	@Test
	void blankAnswersCountAsNotAnswered() {
		ProfileFacts facts = factsFor(Map.of("purchasePrice", " ", "loanPurpose", ""));

		assertThat(facts.answers()).isEmpty();
		assertThat(facts.figures()).isEmpty();
	}

	@Test
	void affordabilityFiguresAppearOnceIncomeAndLoanAreBothReal() {
		ProfileFacts loanOnly = factsFor(merge(Map.of("purchasePrice", 500_000), LOAN_STEP));
		assertThat(names(loanOnly.figures()))
				.contains("loanToValuePercent", "estimatedMonthlyPayment")
				.doesNotContain("debtToIncomePercent", "stressTestResult");

		ProfileFacts everything = factsFor(merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP), INCOME_STEP));
		assertThat(names(everything.figures())).contains("debtToIncomePercent", "stressTestResult");
		ProfileFacts.Figure ltv = everything.figures().stream()
				.filter(f -> f.name().equals("loanToValuePercent")).findFirst().orElseThrow();
		assertThat((Double) ltv.value()).isEqualTo(95.0);
		assertThat(ltv.concern()).contains("High loan-to-value");
	}

	@Test
	void stepFactsOnlyKeepFiguresThatStepFeedsInto() {
		ProfileFacts everything = factsFor(merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP), INCOME_STEP));

		var income = everything.forStep("income", "Income",
				List.of("employmentType", "monthlyGrossIncome", "monthlyDebtPayments"));
		assertThat(names(income.relevantFigures()))
				.contains("debtToIncomePercent", "stressTestResult")
				.doesNotContain("loanToValuePercent", "estimatedPropertyValue");
		assertThat(income.stepAnswers()).containsOnlyKeys("employmentType", "monthlyGrossIncome", "monthlyDebtPayments");
		assertThat(income.otherAnswers()).containsKey("purchasePrice").doesNotContainKey("monthlyGrossIncome");

		var purpose = everything.forStep("purpose", "Purpose", List.of("loanPurpose"));
		assertThat(purpose.relevantFigures()).isEmpty();
		assertThat(purpose.stepUnanswered()).containsExactly("loanPurpose");
	}

	private static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
		var merged = new java.util.HashMap<>(a);
		merged.putAll(b);
		return merged;
	}
}
