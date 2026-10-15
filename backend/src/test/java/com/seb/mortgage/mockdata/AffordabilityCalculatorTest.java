package com.seb.mortgage.mockdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.seb.mortgage.mockdata.AffordabilityCalculator.LoanLimit;
import com.seb.mortgage.mockdata.AffordabilityCalculator.MaxLoan;

/**
 * "How much can I borrow?" on the net-income rules from #31, with the living costs and
 * credit limits from #32. The advisor's affordability tools build on this, so a loan at
 * the calculated maximum must pass the same limits it's worked out from.
 */
class AffordabilityCalculatorTest {

	private static final int TERM = 25;

	private final AffordabilityCalculator calculator = new AffordabilityCalculator();
	private final double rate = new RateService().rateFor(null, null);

	/** Max loan for one adult with no dependants and no entered living costs. */
	private MaxLoan maxLoan(double income, double debts, Double downPayment) {
		return calculator.maxLoan(income, debts, calculator.livingCosts(null, 1, 0), downPayment, rate, TERM);
	}

	@Test
	void principalForIsTheInverseOfMonthlyPayment() {
		double payment = calculator.monthlyPayment(250_000, 4.35, 25);

		assertThat(calculator.principalFor(payment, 4.35, 25)).isCloseTo(250_000, within(0.01));
		assertThat(calculator.principalFor(1_000, 0, 10)).isEqualTo(120_000);
	}

	@Test
	void incomeSetsTheLimitWhenThereIsNoDownPayment() {
		MaxLoan max = maxLoan(4_000, 300, null);

		assertThat(max.limitedBy()).isEqualTo(LoanLimit.INCOME);
		// Half of 4 000 net is 2 000; minus 300 of existing debts leaves 1 700 for the mortgage
		// at the stress-test rate (4.35% + 2 = 6.35%).
		assertThat(AffordabilityCalculator.stressedRate(4.35)).isEqualTo(6.35);
		assertThat(calculator.stressedMonthlyPayment(max.amount(), rate, TERM)).isCloseTo(1_700, within(1.0));
	}

	@Test
	void stressRateIsNeverBelowSixPercent() {
		assertThat(AffordabilityCalculator.stressedRate(3.0)).isEqualTo(6.0);

		MaxLoan max = calculator.maxLoan(4_000, 0, calculator.livingCosts(null, 1, 0), null, 3.0, TERM);
		assertThat(calculator.monthlyPayment(max.amount(), 6.0, TERM)).isCloseTo(2_000, within(1.0));
	}

	@Test
	void bufferSetsTheLimitOnALowIncome() {
		// Half of 1 100 is 550, but 1 100 minus 300 of living costs and the 300 buffer leaves only 500.
		assertThat(maxLoan(1_100, 0, null).limitedBy()).isEqualTo(LoanLimit.STRESS);
	}

	/** Issue #32: unused credit limits still count as debt. */
	@Test
	void creditLimitsCountAsMonthlyDebt() {
		double limitCost = calculator.creditLimitMonthlyCost(5_000);

		assertThat(limitCost).isEqualTo(150);
		assertThat(maxLoan(3_000, limitCost, null).amount())
				.isLessThan(maxLoan(3_000, 0, null).amount());
	}

	/** Issue #32: living costs are what was entered, but never below the household minimum. */
	@Test
	void livingCostsNeverGoBelowTheHouseholdMinimum() {
		// 2 adults and 2 dependants: 2 x 300 + 2 x 200.
		assertThat(calculator.livingCosts(400.0, 2, 2)).isEqualTo(1_000);
		assertThat(calculator.livingCosts(1_400.0, 2, 2)).isEqualTo(1_400);
		assertThat(calculator.livingCosts(null, 1, 0)).isEqualTo(300);
	}

	@Test
	void dependantsLowerTheLimitWhenTheBufferBinds() {
		MaxLoan noKids = calculator.maxLoan(1_500, 0, calculator.livingCosts(null, 1, 0), null, rate, TERM);
		MaxLoan threeKids = calculator.maxLoan(1_500, 0, calculator.livingCosts(null, 1, 3), null, rate, TERM);

		assertThat(threeKids.limitedBy()).isEqualTo(LoanLimit.STRESS);
		assertThat(threeKids.amount()).isLessThan(noKids.amount());
	}

	@Test
	void downPaymentSetsTheLimitWhenItIsSmall() {
		MaxLoan max = maxLoan(6_000, 0, 15_000.0);

		assertThat(max.limitedBy()).isEqualTo(LoanLimit.DOWN_PAYMENT);
		// 15 000 at 85% LTV: loan 85 000 on a 100 000 home.
		assertThat(max.amount()).isEqualTo(85_000);
	}

	@Test
	void noLoanWhenDebtsAlreadyUseTheWholeAllowance() {
		// 1 500 of debts is already half of 3 000 net income.
		MaxLoan max = maxLoan(3_000, 1_500, 50_000.0);

		assertThat(max.amount()).isZero();
		assertThat(max.limitedBy()).isEqualTo(LoanLimit.INCOME);
	}

	@Test
	void noIncomeMeansNoLoan() {
		assertThat(maxLoan(0, 0, null).amount()).isZero();
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
			// household income, debts, down payment, adults, dependants, household costs, credit limits
			"4000, 300,  -,      1, 0, -,    0",
			"4000, 300,  20000,  1, 2, 1200, 5000",
			"4300, 450,  60000,  2, 3, -,    3000",
			"1500, 0,    -,      1, 1, 500,  0",
			"9000, 1200, 400000, 2, 4, 3000, 10000",
	})
	void aLoanAtTheMaximumPassesEveryLimit(double income, double debts, Double downPayment, int adults,
			int dependants, Double householdCosts, double creditLimits) {
		double allDebts = debts + calculator.creditLimitMonthlyCost(creditLimits);
		double livingCosts = calculator.livingCosts(householdCosts, adults, dependants);
		MaxLoan max = calculator.maxLoan(income, allDebts, livingCosts, downPayment, rate, TERM);
		assertThat(max.amount()).isPositive();

		double stressedPayment = calculator.stressedMonthlyPayment(max.amount(), rate, TERM);
		assertThat(calculator.debtToIncome(stressedPayment + allDebts, income))
				.isLessThanOrEqualTo(AffordabilityCalculator.MAX_DTI_PCT);
		assertThat(income - allDebts - livingCosts - stressedPayment)
				.isGreaterThanOrEqualTo(AffordabilityCalculator.MIN_STRESS_BUFFER);
		if (downPayment != null) {
			assertThat(calculator.loanToValue(max.amount(), max.amount() + downPayment))
					.isLessThanOrEqualTo(AffordabilityCalculator.TYPICAL_MAX_LTV_PCT);
		}
	}
}
