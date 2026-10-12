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
 * on the net-income rules from #31,
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
		return controller.maxLoan(new MaxLoanRequest(income, coIncome, debts, downPayment, null, null));
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
		MaxLoanResponse r = controller.maxLoan(new MaxLoanRequest(4_000.0, null, 0.0, null, 3.0, 25));

		assertThat(r.stressRatePct()).isEqualTo(6.0);
		assertThat(calculator.monthlyPayment(r.maxLoanAmount(), 6.0, 25)).isCloseTo(2_000, within(1.0));
	}

	@Test
	void bufferSetsTheLimitOnAVeryLowIncome() {
		// Half of 500 is 250, but 500 minus the 300 buffer leaves only 200.
		assertThat(maxLoan(500.0, null, 0.0, null).limitedBy()).isEqualTo(LoanLimit.STRESS);
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
			"4000, -,    300,  -",
			"4000, -,    300,  20000",
			"2500, 1800, 450,  60000",
			"600,  -,    0,    -",
			"9000, -,    1200, 400000",
	})
	void aLoanAtTheMaximumPassesTheJourneysChecks(double income, Double coIncome, double debts, Double downPayment) {
		MaxLoanResponse max = maxLoan(income, coIncome, debts, downPayment);
		assertThat(max.maxLoanAmount()).isPositive();

		// With no down payment given, the journey still needs a price; any large down payment works.
		double down = downPayment == null ? 1_000_000 : downPayment;
		Map<String, Object> answers = new HashMap<>(Map.of(
				"purchasePrice", max.maxLoanAmount() + down, "downPayment", down,
				"loanAmount", max.maxLoanAmount(), "termYears", max.termYears(), "rateType", "VARIABLE",
				"monthlyNetIncome", income, "monthlyDebtPayments", debts));
		if (coIncome != null) {
			answers.put("hasCoApplicant", true);
			answers.put("coApplicantMonthlyIncome", coIncome);
		}
		LoanApplication application = new LoanApplication("purpose");
		application.putAnswers(answers);

		LoanProfile.Financials f = assembler.assemble(application).financials();
		assertThat(f.stressedDti()).isLessThanOrEqualTo(AffordabilityCalculator.MAX_DTI_PCT);
		assertThat(f.stressTestResult()).isEqualTo("PASS");
		assertThat(f.ltv()).isLessThanOrEqualTo(AffordabilityCalculator.TYPICAL_MAX_LTV_PCT);
	}
}
