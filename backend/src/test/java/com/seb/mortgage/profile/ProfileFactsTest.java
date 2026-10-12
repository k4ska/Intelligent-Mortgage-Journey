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
    /** The income step, plus the household size from "Who is applying?". */
    private static final Map<String, Object> INCOME_STEP = Map.of(
            "employmentType", "EMPLOYED", "monthlyNetIncome", 4_000, "monthlyDebtPayments", 300,
            "totalCreditLimits", 0, "dependants", 0);

    private LoanApplication applicationWith(Map<String, Object> answers) {
        LoanApplication application = new LoanApplication("purpose");
        application.putAnswers(answers);
        return application;
    }

    private ProfileFacts factsFor(Map<String, Object> answers) {
        return assembler.facts(applicationWith(answers));
    }

    private LoanProfile profileFor(Map<String, Object> answers) {
        return assembler.assemble(applicationWith(answers));
    }

    private static List<String> names(List<ProfileFacts.Figure> figures) {
        return figures.stream().map(ProfileFacts.Figure::name).toList();
    }

    @Test
    void freshApplicationHasNoAnswersAndNoFigures() {
        ProfileFacts facts = factsFor(Map.of());

        assertThat(facts.answers()).isEmpty();
        assertThat(facts.figures()).isEmpty();
        assertThat(facts.notAnsweredYet()).contains("purchasePrice", "downPayment", "monthlyNetIncome");
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
                List.of("employmentType", "monthlyNetIncome", "monthlyDebtPayments"));
        assertThat(names(income.relevantFigures()))
                .contains("debtToIncomePercent", "stressTestResult")
                .doesNotContain("loanToValuePercent", "estimatedPropertyValue");
        assertThat(income.stepAnswers()).containsOnlyKeys("employmentType", "monthlyNetIncome", "monthlyDebtPayments");
        assertThat(income.otherAnswers()).containsKey("purchasePrice").doesNotContainKey("monthlyNetIncome");

        var purpose = everything.forStep("purpose", "Purpose", List.of("loanPurpose"));
        assertThat(purpose.relevantFigures()).isEmpty();
        assertThat(purpose.stepUnanswered()).containsExactly("loanPurpose");
    }

    @Test
    void coApplicantChangesHouseholdAffordabilityAndAppearsInProfile() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP),
                Map.of("employmentType", "EMPLOYED", "monthlyNetIncome", 3_500,
                        "monthlyDebtPayments", 300));
        LoanProfile single = profileFor(base);
        LoanProfile joint = profileFor(merge(base, Map.of("hasCoApplicant", true,
                "coApplicantName", "Sam", "coApplicantMonthlyIncome", 4_000,
                "coApplicantMonthlyDebtPayments", 100)));

        assertThat(single.applicants()).hasSize(1);
        assertThat(joint.applicants()).hasSize(2);
        assertThat(joint.applicants().get(1).name()).isEqualTo("Sam");
        assertThat(joint.applicants().get(1).employment()).isNull();
        assertThat(joint.applicants().get(1).monthlyNetIncome()).isEqualTo(4_000);
        assertThat(joint.applicants().get(1).monthlyDebtPayments()).isEqualTo(100);

        assertThat(joint.financials().dti()).isLessThan(single.financials().dti());
        assertThat(joint.financials().monthlyBufferAfterStress())
                .isGreaterThan(single.financials().monthlyBufferAfterStress());
        assertThat(single.financials().stressTestResult()).isEqualTo("FAIL");
        assertThat(joint.financials().stressTestResult()).isEqualTo("PASS");
        assertThat(single.riskIndicators()).anyMatch(r -> r.contains("Fails the stress test"));
        assertThat(joint.riskIndicators()).noneMatch(r -> r.contains("Fails the stress test"));
    }

    @Test
    void coApplicantAffordabilityWaitsForIncomeAndDebts() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP), INCOME_STEP);
        Map<String, Object> coSelected = merge(base, Map.of("hasCoApplicant", true, "coApplicantName", "Sam"));

        ProfileFacts missingIncome = factsFor(merge(coSelected,
                Map.of("coApplicantMonthlyDebtPayments", 0)));
        assertThat(names(missingIncome.figures()))
                .contains("estimatedMonthlyPayment", "stressedMonthlyPayment")
                .doesNotContain("debtToIncomePercent", "monthlyBufferAfterStress", "stressTestResult");
        assertThat(missingIncome.notAnsweredYet()).contains("coApplicantMonthlyIncome");

        ProfileFacts missingDebt = factsFor(merge(coSelected,
                Map.of("coApplicantMonthlyIncome", 3_000)));
        assertThat(names(missingDebt.figures()))
                .doesNotContain("debtToIncomePercent", "monthlyBufferAfterStress", "stressTestResult");
        assertThat(missingDebt.notAnsweredYet()).contains("coApplicantMonthlyDebtPayments");

        ProfileFacts complete = factsFor(merge(coSelected,
                Map.of("coApplicantMonthlyIncome", 3_000, "coApplicantMonthlyDebtPayments", 0,
                        "coApplicantCreditLimits", 0)));
        assertThat(names(complete.figures()))
                .contains("debtToIncomePercent", "monthlyBufferAfterStress", "stressTestResult");
        ProfileFacts.Figure dti = complete.figures().stream()
                .filter(f -> f.name().equals("debtToIncomePercent")).findFirst().orElseThrow();
        assertThat(dti.basedOn()).contains("coApplicantMonthlyIncome", "coApplicantMonthlyDebtPayments");
        assertThat(complete.forStep("co-applicant", "Co-applicant",
                List.of("coApplicantName", "coApplicantMonthlyIncome", "coApplicantMonthlyDebtPayments"))
                .relevantFigures()).extracting(ProfileFacts.Figure::name)
                .contains("debtToIncomePercent", "stressTestResult");
    }

    @Test
    void noCoApplicantDoesNotRequireOrCountCoApplicantFields() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP), INCOME_STEP);
        Map<String, Object> staleAnswers = merge(base, Map.of("hasCoApplicant", false,
                "coApplicantName", "Sam", "coApplicantMonthlyIncome", 9_000,
                "coApplicantMonthlyDebtPayments", 5_000));

        assertThat(profileFor(staleAnswers).applicants()).hasSize(1);
        assertThat(profileFor(staleAnswers).financials().dti()).isEqualTo(profileFor(base).financials().dti());
        ProfileFacts facts = factsFor(merge(base, Map.of("hasCoApplicant", false)));
        assertThat(facts.notAnsweredYet()).doesNotContain(
                "coApplicantName", "coApplicantMonthlyIncome", "coApplicantMonthlyDebtPayments");
        assertThat(names(facts.figures())).contains("debtToIncomePercent", "stressTestResult");
    }

    /** Issue #31: self-employed applicants count a twelfth of last year's net profit as monthly net income. */
    @Test
    void selfEmployedIncomeIsATwelfthOfLastYearsNetProfit() {
        Map<String, Object> loan = merge(Map.of("purchasePrice", 500_000), LOAN_STEP);
        Map<String, Object> selfEmployed = merge(loan, Map.of("employmentType", "SELF_EMPLOYED",
                "monthlyDebtPayments", 300, "yearsTrading", 4, "lastYearNetProfit", 42_000,
                "totalCreditLimits", 0, "dependants", 0));

        LoanProfile profile = profileFor(selfEmployed);
        assertThat(profile.applicants().get(0).monthlyNetIncome()).isEqualTo(3_500);
        assertThat(profile.financials().dti()).isEqualTo(profileFor(merge(loan, Map.of(
                "employmentType", "EMPLOYED", "monthlyNetIncome", 3_500, "monthlyDebtPayments", 300)))
                .financials().dti());

        ProfileFacts facts = factsFor(selfEmployed);
        assertThat(facts.notAnsweredYet()).doesNotContain("monthlyNetIncome", "lastYearNetProfit");
        ProfileFacts.Figure dti = facts.figures().stream()
                .filter(f -> f.name().equals("debtToIncomePercent")).findFirst().orElseThrow();
        assertThat(dti.basedOn()).contains("lastYearNetProfit").doesNotContain("monthlyNetIncome");

        ProfileFacts noProfitYet = factsFor(merge(loan, Map.of("employmentType", "SELF_EMPLOYED",
                "monthlyDebtPayments", 300)));
        assertThat(noProfitYet.notAnsweredYet()).contains("lastYearNetProfit").doesNotContain("monthlyNetIncome");
        assertThat(names(noProfitYet.figures())).doesNotContain("debtToIncomePercent", "stressTestResult");
    }

    /** Issue #31: the 50% limit applies at the stressed rate (contract rate + 2 pp, at least 6%). */
    @Test
    void stressTestFailsWhenStressedPaymentsTakeMoreThanHalfOfNetIncome() {
        // 4.35% variable, so the stress test runs at 6.35%: about 1 664/month on 250 000 over 25 years.
        Map<String, Object> loan = merge(Map.of("purchasePrice", 300_000), Map.of(
                "downPayment", 50_000, "loanAmount", 250_000, "termYears", 25, "rateType", "VARIABLE"));
        LoanProfile.Financials passes = profileFor(merge(loan, Map.of(
                "employmentType", "EMPLOYED", "monthlyNetIncome", 3_400, "monthlyDebtPayments", 0))).financials();
        LoanProfile.Financials fails = profileFor(merge(loan, Map.of(
                "employmentType", "EMPLOYED", "monthlyNetIncome", 3_200, "monthlyDebtPayments", 0))).financials();

        assertThat(passes.stressedMonthlyPayment()).isCloseTo(1_664, org.assertj.core.api.Assertions.within(1.0));
        assertThat(passes.stressedDti()).isLessThanOrEqualTo(50);
        assertThat(passes.stressTestResult()).isEqualTo("PASS");
        assertThat(fails.stressedDti()).isGreaterThan(50);
        assertThat(fails.stressTestResult()).isEqualTo("FAIL");
        // Above 50% under stress but well within it at today's rate: the stress test is what decides.
        assertThat(fails.dti()).isLessThan(50);
    }

    /** Issue #32: credit limits count as debt, and living costs never go below the household minimum. */
    @Test
    void creditLimitsAndLivingCostsCountInAffordability() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 300_000), Map.of(
                "downPayment", 50_000, "loanAmount", 250_000, "termYears", 25, "rateType", "VARIABLE")), INCOME_STEP);
        LoanProfile plain = profileFor(base);
        LoanProfile withLimits = profileFor(merge(base, Map.of("totalCreditLimits", 5_000)));
        LoanProfile family = profileFor(merge(base, Map.of("dependants", 3)));

        // 3% of 5 000 counts as 150 a month of debt, at today's rate and under stress.
        assertThat(withLimits.financials().creditLimitMonthlyCost()).isEqualTo(150);
        assertThat(withLimits.applicants().get(0).creditLimits()).isEqualTo(5_000);
        assertThat(withLimits.financials().stressedDti()).isGreaterThan(plain.financials().stressedDti());
        assertThat(withLimits.riskIndicators()).anyMatch(r -> r.contains("limits count as 150/month"));

        // 1 adult: 300; plus 3 dependants: 900. Less buffer, same DTI (living costs aren't debt).
        assertThat(plain.financials().monthlyLivingCosts()).isEqualTo(300);
        assertThat(family.financials().monthlyLivingCosts()).isEqualTo(900);
        assertThat(family.household().minimumMonthlyCosts()).isEqualTo(900);
        assertThat(family.financials().monthlyBufferAfterStress())
                .isEqualTo(plain.financials().monthlyBufferAfterStress() - 600);
        assertThat(family.financials().dti()).isEqualTo(plain.financials().dti());
    }

    @Test
    void enteredHouseholdCostsBelowTheMinimumAreRaisedAndFlagged() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP),
                merge(INCOME_STEP, Map.of("dependants", 2)));

        LoanProfile low = profileFor(merge(base, Map.of("monthlyHouseholdCosts", 400)));
        LoanProfile high = profileFor(merge(base, Map.of("monthlyHouseholdCosts", 1_500)));
        LoanProfile blank = profileFor(merge(base, Map.of("monthlyHouseholdCosts", " ")));

        assertThat(low.financials().monthlyLivingCosts()).isEqualTo(700);
        assertThat(low.riskIndicators()).anyMatch(r -> r.contains("below the 700/month lenders assume"));
        assertThat(high.financials().monthlyLivingCosts()).isEqualTo(1_500);
        assertThat(blank.household().enteredMonthlyCosts()).isNull();
        assertThat(blank.financials().monthlyLivingCosts()).isEqualTo(700);
        assertThat(blank.riskIndicators()).noneMatch(r -> r.contains("lenders assume"));

        ProfileFacts.Figure living = factsFor(merge(base, Map.of("monthlyHouseholdCosts", 400))).figures().stream()
                .filter(f -> f.name().equals("monthlyLivingCosts")).findFirst().orElseThrow();
        assertThat(living.concern()).contains("so the minimum is used");
        assertThat(living.basedOn()).contains("dependants", "monthlyHouseholdCosts");
    }

    @Test
    void affordabilityWaitsForDependantsAndCreditLimitsButNotOptionalHouseholdCosts() {
        Map<String, Object> base = merge(merge(Map.of("purchasePrice", 500_000), LOAN_STEP), INCOME_STEP);
        Map<String, Object> noLimits = new java.util.HashMap<>(base);
        noLimits.remove("totalCreditLimits");
        Map<String, Object> noDependants = new java.util.HashMap<>(base);
        noDependants.remove("dependants");

        assertThat(names(factsFor(noLimits).figures())).doesNotContain("creditLimitMonthlyCost", "stressTestResult");
        assertThat(factsFor(noLimits).notAnsweredYet()).contains("totalCreditLimits");
        assertThat(names(factsFor(noDependants).figures())).doesNotContain("monthlyLivingCosts", "stressTestResult");
        assertThat(factsFor(noDependants).notAnsweredYet()).contains("dependants");
        assertThat(names(factsFor(base).figures())).contains("monthlyLivingCosts", "creditLimitMonthlyCost",
                "stressTestResult");
        assertThat(factsFor(base).notAnsweredYet()).doesNotContain("monthlyHouseholdCosts");
    }

    private static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        var merged = new java.util.HashMap<>(a);
        merged.putAll(b);
        return merged;
    }
}