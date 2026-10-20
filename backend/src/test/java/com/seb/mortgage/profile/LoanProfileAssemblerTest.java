package com.seb.mortgage.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.AffordabilityCalculator.LoanLimit;
import com.seb.mortgage.mockdata.RateService;
import com.seb.mortgage.profile.ProfileField.Source;
import com.seb.mortgage.profile.ProfileField.Status;

/** Building the loan profile from field rows, and the checks on it: no Spring, no LLM. */
class LoanProfileAssemblerTest {

	private final AffordabilityCalculator calculator = new AffordabilityCalculator();
	private final LoanProfileAssembler assembler = new LoanProfileAssembler(calculator, new RateService());
	private final LoanProfileAssembler.Terms terms = assembler.terms(1.75);

	private static ProfileFieldView confirmed(String key, String value) {
		return new ProfileFieldView(key, value, Source.CHAT, Status.CONFIRMED);
	}

	/** A single buyer earning 4000 with 50 000 saved, no debts and 600 of living costs. */
	private static List<ProfileFieldView> household(String... overrides) {
		List<ProfileFieldView> fields = new ArrayList<>(List.of(
				confirmed("netIncome", "4000"),
				confirmed("savings", "50000"),
				confirmed("monthlyDebtPayments", "0"),
				confirmed("creditCardLimits", "0"),
				confirmed("hasCoApplicant", "no"),
				confirmed("dependants", "0"),
				confirmed("monthlyLivingCosts", "600")));
		for (int i = 0; i < overrides.length; i += 2) {
			String key = overrides[i];
			fields.removeIf(field -> field.key().equals(key));
			fields.add(confirmed(key, overrides[i + 1]));
		}
		return fields;
	}

	private static List<ProfileFieldView> property(String price) {
		return List.of(confirmed("type", "APARTMENT"), confirmed("price", price), confirmed("location", "Tallinn"));
	}

	private LoanProfileAssembler.Checked checked(List<ProfileFieldView> fields, List<ProfileFieldView> property) {
		return (LoanProfileAssembler.Checked) assembler.check(fields, property, terms);
	}

	@Test
	void theRateIsTheVariableRatePlusTheMargin() {
		assertThat(terms.variableRatePct()).isEqualTo(4.35);
		assertThat(terms.ratePct()).isEqualTo(6.1);
	}

	@Test
	void listsEveryMissingFactInTheOrderToAskAndNeverFillsAGap() {
		LoanProfileAssembler.Check result = assembler.check(List.of(confirmed("netIncome", "4000")), List.of(), terms);

		assertThat(result).isEqualTo(new LoanProfileAssembler.Missing(List.of("savings", "monthlyDebtPayments",
				"creditCardLimits", "hasCoApplicant", "dependants", "monthlyLivingCosts", "price"), List.of()));
	}

	@Test
	void asksForTheCoApplicantsIncomeOnlyWhenThereIsOne() {
		LoanProfileAssembler.Check withCoApplicant = assembler.check(household("hasCoApplicant", "yes"),
				property("200000"), terms);

		assertThat(withCoApplicant)
				.isEqualTo(new LoanProfileAssembler.Missing(List.of("coApplicantNetIncome"), List.of()));
		assertThat(assembler.check(household(), property("200000"), terms))
				.isInstanceOf(LoanProfileAssembler.Checked.class);
	}

	@Test
	void anUncertainValueIsNotUsedUntilTheUserConfirmsIt() {
		List<ProfileFieldView> pasted = List.of(new ProfileFieldView("price", "200000", Source.CHAT, Status.UNCERTAIN));

		assertThat(assembler.check(household(), pasted, terms))
				.isEqualTo(new LoanProfileAssembler.Missing(List.of(), List.of("price")));
	}

	@Test
	void buildsTheLoanProfileFromTheRows() {
		LoanProfileAssembler.Checked result = checked(household("employment", "employed"), property("200000"));
		LoanProfile profile = result.profile();

		assertThat(result.affordable()).isTrue();
		assertThat(profile.applicants()).containsExactly(new LoanProfile.Applicant(null, "employed", 4000, 0, 0));
		assertThat(profile.property()).isEqualTo(new LoanProfile.Property("APARTMENT", "Tallinn", 200000, 200000));
		assertThat(profile.loanRequest()).isEqualTo(new LoanProfile.LoanRequest(150000, 25, "VARIABLE", "purchase"));
		assertThat(profile.financials().downPayment()).isEqualTo(50000);
		assertThat(profile.financials().ltv()).isEqualTo(75.0);
		assertThat(profile.financials().estimatedMonthlyPayment())
				.isEqualTo(Math.round(calculator.monthlyPayment(150000, 6.1, 25) * 100) / 100.0);
		assertThat(profile.financials().stressedMonthlyPayment())
				.isEqualTo(Math.round(calculator.monthlyPayment(150000, 8.1, 25) * 100) / 100.0);
		assertThat(profile.financials().stressTestResult()).isEqualTo("PASS");
		assertThat(result.maxLoan()).isEqualTo(calculator.maxLoan(4000, 0, 600, 50000.0, 6.1, 25));
	}

	@Test
	void livingCostsAreAtLeastTheMinimumForTheHousehold() {
		LoanProfile profile = checked(household("hasCoApplicant", "yes", "coApplicantNetIncome", "2000",
				"dependants", "2", "monthlyLivingCosts", "500"), property("200000")).profile();

		assertThat(profile.applicants()).hasSize(2);
		assertThat(profile.household()).isEqualTo(new LoanProfile.Household(2, 2, 500.0, 1000));
		assertThat(profile.financials().monthlyLivingCosts()).isEqualTo(1000);
	}

	@Test
	void creditCardLimitsCountAsAMonthlyPayment() {
		LoanProfile profile = checked(household("creditCardLimits", "5000"), property("200000")).profile();

		assertThat(profile.financials().creditLimitMonthlyCost()).isEqualTo(150);
	}

	@Test
	void tooLittleIncomeFailsTheFiftyPercentLimitAndTheBuffer() {
		LoanProfileAssembler.Checked result = checked(household("netIncome", "1500", "monthlyLivingCosts", "300"),
				property("200000"));

		assertThat(result.affordable()).isFalse();
		assertThat(result.profile().riskIndicators()).containsExactly(LoanProfileAssembler.PAYMENTS_TOO_HIGH,
				LoanProfileAssembler.BUFFER_TOO_SMALL);
	}

	@Test
	void tooSmallADownPaymentFailsOnlyTheLoanToValue() {
		LoanProfileAssembler.Checked result = checked(household("savings", "20000"), property("200000"));

		assertThat(result.affordable()).isFalse();
		assertThat(result.profile().riskIndicators()).containsExactly(LoanProfileAssembler.DOWN_PAYMENT_TOO_LOW);
	}

	@Test
	void theRangeIsWiderWithTheLongestTerm() {
		LoanProfileAssembler.PriceRange range = (LoanProfileAssembler.PriceRange) assembler.range(household(), terms);

		assertThat(range.standard()).isEqualTo(calculator.maxLoan(4000, 0, 600, 50000.0, 6.1, 25));
		assertThat(range.standardMaxPrice()).isEqualTo(range.standard().amount() + 50000);
		assertThat(range.longestMaxPrice()).isGreaterThan(range.standardMaxPrice());
		assertThat(range.longest().limitedBy()).isEqualTo(LoanLimit.INCOME);
	}

	@Test
	void withoutSavingsNothingCanBeBorrowed() {
		LoanProfileAssembler.PriceRange range = (LoanProfileAssembler.PriceRange) assembler.range(
				household("savings", "0"), terms);

		assertThat(range.longest()).isEqualTo(new AffordabilityCalculator.MaxLoan(0, LoanLimit.DOWN_PAYMENT));
	}

	@Test
	void theRangeNeedsNoProperty() {
		assertThat(assembler.range(List.of(confirmed("netIncome", "4000")), terms))
				.isEqualTo(new LoanProfileAssembler.Missing(List.of("savings", "monthlyDebtPayments",
						"creditCardLimits", "hasCoApplicant", "dependants", "monthlyLivingCosts"), List.of()));
	}
}
