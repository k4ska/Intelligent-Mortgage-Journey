package com.seb.mortgage.mockdata;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.seb.mortgage.application.LoanApplication;
import com.seb.mortgage.mockdata.AffordabilityCalculator.LoanLimit;
import com.seb.mortgage.mockdata.CalculatorController.MaxLoanRequest;
import com.seb.mortgage.mockdata.CalculatorController.MaxLoanResponse;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.LoanProfileAssembler;

/**
 * Issue #29: "How much can I borrow?" must never promise a loan the journey then flags,
 * on the net-income rules from #31 and with the living costs and credit limits from #32,
 * so a loan at the calculated maximum has to pass the journey's own affordability checks.
 */
class MaxLoanCalculatorTest {

	private final AffordabilityCalculator calculator = new AffordabilityCalculator();
	private final RateService rateService = new RateService();
	private final CalculatorController controller = new CalculatorController(calculator, rateService);
	private final LoanProfileAssembler assembler = new LoanProfileAssembler(calculator,
			new PropertyValuationService(), rateService, new DocumentChecklistService());

	@Test
	void aSelfEmployedLoanAtTheMaximumPassesTooWhenIncomeIsProfitOverTwelve() {
		MaxLoanResponse max = maxLoan(3_000.0, null, 200.0, null);
		LoanApplication application = new LoanApplication("purpose");
		application.putAnswers(Map.of("purchasePrice", max.maxLoanAmount() + 1_000_000, "downPayment", 1_000_000,
				"loanAmount", max.maxLoanAmount(), "termYears", 25, "rateType", "VARIABLE",
				"employmentType", "SELF_EMPLOYED", "lastYearNetProfit", 36_000, "monthlyDebtPayments", 200));

		LoanProfile.Financials f = assembler.assemble(application).financials();
		assertThat(f.stressedDti()).isLessThanOrEqualTo(AffordabilityCalculator.MAX_DTI_PCT);
		assertThat(f.stressTestResult()).isEqualTo("PASS");
	}

	private MaxLoanResponse maxLoan(Double income, Double coIncome, Double debts, Double downPayment) {
		return maxLoan(income, coIncome, debts, downPayment, null, null, null);
	}

	private MaxLoanResponse maxLoan(Double income, Double coIncome, Double debts, Double downPayment,
			Integer dependants, Double householdCosts, Double creditLimits) {
		return controller.maxLoan(new MaxLoanRequest(income, coIncome, debts, downPayment, null, null,
				dependants, householdCosts, creditLimits));
	}

	@Test
	void principalForIsTheInverseOfMonthlyPayment() {
		double payment = calculator.monthlyPayment(250_000, 4.35, 25);

		assertThat(calculator.principalFor(payment, 4.35, 25)).isCloseTo(250_000, within(0.01));
		assertThat(calculator.principalFor(1_000, 0, 10)).isEqualTo(120_000);
	}

	@Test
	void incomeSetsTheLimitWhenThereIsNoDownPayment() {
		MaxLoanResponse r = maxLoan(4_000.0, null, 300.0, null);

		assertThat(r.limitedBy()).isEqualTo(LoanLimit.INCOME);
		// Half of 4 000 net is 2 000; minus 300 of existing debts leaves 1 700 for the mortgage
		// at the stress-test rate (4.35% + 2 = 6.35%).
		assertThat(r.stressRatePct()).isEqualTo(6.35);
		assertThat(calculator.stressedMonthlyPayment(r.maxLoanAmount(), r.annualRatePct(), r.termYears()))
				.isCloseTo(1_700, within(1.0));
		assertThat(r.maxPropertyPrice()).isNull();
	}

	@Test
	void stressRateIsNeverBelowSixPercent() {
		MaxLoanResponse r = controller.maxLoan(new MaxLoanRequest(4_000.0, null, 0.0, null, 3.0, 25, null, null, null));

		assertThat(r.stressRatePct()).isEqualTo(6.0);
		assertThat(calculator.monthlyPayment(r.maxLoanAmount(), 6.0, 25)).isCloseTo(2_000, within(1.0));
	}

	@Test
	void bufferSetsTheLimitOnALowIncome() {
		// Half of 1 100 is 550, but 1 100 minus 300 of living costs and the 300 buffer leaves only 500.
		assertThat(maxLoan(1_100.0, null, 0.0, null).limitedBy()).isEqualTo(LoanLimit.STRESS);
	}

	/** Issue #32: unused credit limits still count as debt. */
	@Test
	void creditLimitsCountAsMonthlyDebt() {
		MaxLoanResponse withLimits = maxLoan(3_000.0, null, 0.0, null, null, null, 5_000.0);

		assertThat(withLimits.creditLimitMonthlyCost()).isEqualTo(150);
		// The same as 150 of real monthly debt payments.
		assertThat(withLimits.maxLoanAmount()).isEqualTo(maxLoan(3_000.0, null, 150.0, null).maxLoanAmount())
				.isLessThan(maxLoan(3_000.0, null, 0.0, null).maxLoanAmount());
	}

	/** Issue #32: living costs are what was entered, but never below the household minimum. */
	@Test
	void livingCostsNeverGoBelowTheHouseholdMinimum() {
		// 2 adults (a partner's income was given) and 2 dependants: 2 x 300 + 2 x 200.
		assertThat(maxLoan(1_500.0, 1_000.0, 0.0, null, 2, 400.0, null).monthlyLivingCosts()).isEqualTo(1_000);
		assertThat(maxLoan(1_500.0, 1_000.0, 0.0, null, 2, 1_400.0, null).monthlyLivingCosts()).isEqualTo(1_400);
		assertThat(maxLoan(1_500.0, null, 0.0, null).monthlyLivingCosts()).isEqualTo(300);
	}

	@Test
	void dependantsLowerTheLimitWhenTheBufferBinds() {
		MaxLoanResponse noKids = maxLoan(1_500.0, null, 0.0, null, 0, null, null);
		MaxLoanResponse threeKids = maxLoan(1_500.0, null, 0.0, null, 3, null, null);

		assertThat(threeKids.limitedBy()).isEqualTo(LoanLimit.STRESS);
		assertThat(threeKids.maxLoanAmount()).isLessThan(noKids.maxLoanAmount());
	}

	@Test
	void downPaymentSetsTheLimitWhenItIsSmall() {
		MaxLoanResponse r = maxLoan(6_000.0, null, 0.0, 15_000.0);

		assertThat(r.limitedBy()).isEqualTo(LoanLimit.DOWN_PAYMENT);
		// 15 000 at 85% LTV: loan 85 000 on a 100 000 home.
		assertThat(r.maxLoanAmount()).isEqualTo(85_000);
		assertThat(r.maxPropertyPrice()).isEqualTo(100_000);
	}

	@Test
	void coApplicantIncomeRaisesTheLimit() {
		assertThat(maxLoan(3_000.0, 2_000.0, 0.0, null).maxLoanAmount())
				.isEqualTo(maxLoan(5_000.0, null, 0.0, null).maxLoanAmount())
				.isGreaterThan(maxLoan(3_000.0, null, 0.0, null).maxLoanAmount());
	}

	@Test
	void noLoanWhenDebtsAlreadyUseTheWholeAllowance() {
		MaxLoanResponse r = maxLoan(3_000.0, null, 1_500.0, 50_000.0);
		// 1 500 of debts is already half of 3 000 net income.

		assertThat(r.maxLoanAmount()).isZero();
		assertThat(r.monthlyPayment()).isZero();
		assertThat(r.limitedBy()).isEqualTo(LoanLimit.INCOME);
	}

	@Test
	void noIncomeMeansNoLoan() {
		assertThat(maxLoan(null, null, null, null).maxLoanAmount()).isZero();
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
			// income, partner, debts, down payment, dependants, household costs, credit limits
			"4000, -,    300,  -,      0, -,    0",
			"4000, -,    300,  20000,  2, 1200, 5000",
			"2500, 1800, 450,  60000,  3, -,    3000",
			"1500, -,    0,    -,      1, 500,  0",
			"9000, -,    1200, 400000, 4, 3000, 10000",
	})
	void aLoanAtTheMaximumPassesTheJourneysChecks(double income, Double coIncome, double debts, Double downPayment,
			int dependants, Double householdCosts, double creditLimits) {
		MaxLoanResponse max = maxLoan(income, coIncome, debts, downPayment, dependants, householdCosts, creditLimits);
		assertThat(max.maxLoanAmount()).isPositive();

		// With no down payment given, the journey still needs a price; any large down payment works.
		double down = downPayment == null ? 1_000_000 : downPayment;
		Map<String, Object> answers = new HashMap<>(Map.of(
				"purchasePrice", max.maxLoanAmount() + down, "downPayment", down,
				"loanAmount", max.maxLoanAmount(), "termYears", max.termYears(), "rateType", "VARIABLE",
				"monthlyNetIncome", income, "monthlyDebtPayments", debts,
				"dependants", dependants, "totalCreditLimits", creditLimits));
		if (householdCosts != null) {
			answers.put("monthlyHouseholdCosts", householdCosts);
		}
		if (coIncome != null) {
			answers.put("hasCoApplicant", true);
			answers.put("coApplicantMonthlyIncome", coIncome);
			answers.put("coApplicantCreditLimits", 0);
		}
		LoanApplication application = new LoanApplication("purpose");
		application.putAnswers(answers);

		LoanProfile.Financials f = assembler.assemble(application).financials();
		assertThat(f.stressedDti()).isLessThanOrEqualTo(AffordabilityCalculator.MAX_DTI_PCT);
		assertThat(f.stressTestResult()).isEqualTo("PASS");
		assertThat(f.ltv()).isLessThanOrEqualTo(AffordabilityCalculator.TYPICAL_MAX_LTV_PCT);
	}
}
