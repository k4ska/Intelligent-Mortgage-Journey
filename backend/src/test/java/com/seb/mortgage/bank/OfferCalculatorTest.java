package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.mockdata.RateService;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.property.PropertyView;

class OfferCalculatorTest {

	private final BankCatalogService catalog = new BankCatalogService();
	private final OfferCalculator calculator = new OfferCalculator(new RateService(),
			new AffordabilityCalculator());

	private OfferCalculator.Snapshot snapshot(String... values) {
		return snapshot(List.of(), values);
	}

	private OfferCalculator.Snapshot snapshot(List<OfferCalculator.DocumentSnapshot> documents,
			String... values) {
		List<ProfileFieldView> fields = new java.util.ArrayList<>();
		for (int i = 0; i < values.length; i += 2) {
			fields.add(new ProfileFieldView(values[i], values[i + 1], ProfileField.Source.CHAT,
					ProfileField.Status.CONFIRMED));
		}
		PropertyView property = new PropertyView(null, List.of(
				new ProfileFieldView("type", "APARTMENT", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED),
				new ProfileFieldView("price", "200000", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED),
				new ProfileFieldView("location", "Tallinn", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED)));
		return new OfferCalculator.Snapshot(fields, property, documents);
	}

	@Test
	void asksForIncomeWhenTheSnapshotIsIncomplete() {
		OfferCalculator.Decision result = calculator.calculate(snapshot("savings", "40000"),
				catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.QUESTION);
		assertThat(result.reason()).contains("monthly net income");
	}

	@Test
	void asksForAConfirmedValuationWhenItIsStillUnconfirmed() {
		OfferCalculator.Decision result = calculator.calculate(
				snapshot(List.of(new OfferCalculator.DocumentSnapshot("valuation", "uploaded")),
						"netIncome", "5000", "savings", "40000"),
				catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.QUESTION);
		assertThat(result.reason()).contains("confirmed valuation");
	}

	@Test
	void declinesWhenDownPaymentIsBelowTheBankMinimum() {
		OfferCalculator.Decision result = calculator.calculate(snapshot("netIncome", "5000", "savings", "10000"),
				catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.DECLINE);
		assertThat(result.reason()).contains("minimum");
	}

	@Test
	void declinesWhenAffordabilityDoesNotCarryTheRequestedLoan() {
		OfferCalculator.Decision result = calculator.calculate(snapshot("netIncome", "1200", "savings", "40000"),
				catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.DECLINE);
		assertThat(result.reason()).contains("affordability");
	}

	@Test
	void countsTheLivingCostsTheUserEntered() {
		OfferCalculator.Decision result = calculator.calculate(
				snapshot("netIncome", "5000", "savings", "40000", "monthlyLivingCosts", "3600"), catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.DECLINE);
		assertThat(result.reason()).contains("affordability");
	}

	@Test
	void countsCreditCardLimitsAsAMonthlyDebt() {
		OfferCalculator.Decision result = calculator.calculate(
				snapshot("netIncome", "5000", "savings", "40000", "creditCardLimits", "45000"), catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.DECLINE);
		assertThat(result.reason()).contains("affordability");
	}

	@Test
	void declinesWhenTheRequestedLoanExceedsTheBankLtv() {
		BankCatalogService.Bank strictLtvBank = new BankCatalogService.Bank("test", "Test Bank",
				"neutral", "Received.", 1.65, 1.35, 70, 10,
				BankCatalogService.Strictness.STANDARD, 2);

		OfferCalculator.Decision result = calculator.calculate(
				snapshot("netIncome", "5000", "savings", "40000"), strictLtvBank);

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.DECLINE);
		assertThat(result.reason()).contains("loan-to-value");
	}

	@Test
	void calculatesAnOfferUsingTheAnnuityFormula() {
		OfferCalculator.Decision result = calculator.calculate(snapshot("netIncome", "5000", "savings", "40000"),
				catalog.find("seb"));

		assertThat(result.type()).isEqualTo(OfferCalculator.DecisionType.OFFER);
		OfferCalculator.Offer offer = result.offer();
		assertThat(offer.amount()).isEqualTo(160000);
		assertThat(offer.ratePct()).isEqualTo(6.0);
		assertThat(offer.monthlyPayment()).isCloseTo(
				new AffordabilityCalculator().monthlyPayment(160000, 6.0, 25), org.assertj.core.data.Offset.offset(0.01));
		assertThat(offer.conditions()).isNotEmpty();
		assertThat(offer.validUntil()).isNotNull();
	}

	@Test
	void everyBankProducesDifferentTermsForTheSameApplication() {
		var snapshot = snapshot("netIncome", "5000", "savings", "40000");

		assertThat(catalog.banks().stream()
				.map(bank -> calculator.calculate(snapshot, bank).offer().marginPct())
				.distinct()).hasSize(5);
	}

	@Test
	void negotiationMarginReachesTheFloorOnTheLastRound() {
		BankCatalogService.Bank seb = catalog.find("seb");

		assertThat(calculator.lowestMargin(seb, 0)).isEqualTo(seb.baseMarginPct());
		assertThat(calculator.lowestMargin(seb, seb.maxNegotiationRounds()))
				.isEqualTo(seb.lowestMarginPct());
		assertThatThrownBy(() -> calculator.lowestMargin(seb, seb.maxNegotiationRounds() + 1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
