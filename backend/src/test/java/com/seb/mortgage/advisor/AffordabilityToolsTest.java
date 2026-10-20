package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileField.Source;
import com.seb.mortgage.profile.ProfileField.Status;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;

/** The advisor's affordability tools (#65), called directly: no LLM involved. */
@SpringBootTest
class AffordabilityToolsTest {

	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired BankCatalogService banks;
	@Autowired ListingReader listings;
	@Autowired LoanProfileAssembler loans;
	@Autowired DocumentService documents;
	@Autowired AffordabilityCalculator calculator;

	private final UUID userId = UUID.randomUUID();
	private final List<StreamEvent> events = new ArrayList<>();

	private AdvisorTools tools() {
		return new AdvisorTools(userId, profile, property, listings, banks, loans, documents,
				UserStatements.from(List.of(), ""), events::add);
	}

	/** A single buyer earning 4000 with 50 000 saved, no debts and 600 of living costs. */
	private void household(String netIncome) {
		save(ProfileKey.NET_INCOME, netIncome);
		save(ProfileKey.SAVINGS, "50000");
		save(ProfileKey.MONTHLY_DEBT_PAYMENTS, "0");
		save(ProfileKey.CREDIT_CARD_LIMITS, "0");
		save(ProfileKey.HAS_CO_APPLICANT, "no");
		save(ProfileKey.DEPENDANTS, "0");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "600");
	}

	private void save(ProfileKey key, String value) {
		profile.save(userId, key, value, Source.CHAT, Status.CONFIRMED);
	}

	private void listing(String price, String size) {
		property.replaceFromListing(userId, "https://www.kv.ee/3612345", Map.of(PropertyKey.TYPE, "APARTMENT",
				PropertyKey.PRICE, price, PropertyKey.LOCATION, "Tallinn, Kesklinn", PropertyKey.SIZE, size));
	}

	@Test
	void namesTheMissingFactsInsteadOfGuessing() {
		save(ProfileKey.NET_INCOME, "4000");

		String note = tools().checkAffordability();

		assertThat(note).startsWith("Can't tell yet. Still missing: savings, monthlyDebtPayments, creditCardLimits, "
				+ "hasCoApplicant, dependants, monthlyLivingCosts, price.");
		assertThat(events).isEmpty();
	}

	@Test
	void theAnswerCarriesTheFiguresFromTheCalculator() {
		household("4000");
		listing("200000", "50");

		String note = tools().checkAffordability();

		String payment = Long.toString(Math.round(calculator.monthlyPayment(150000, 6.1, 25)));
		assertThat(note).startsWith("Affordable: yes. Price 200000, down payment 50000, loan 150000")
				.contains("25 years at 6.1% a year (the variable rate of 4.35% plus a typical bank margin of 1.75%)")
				.contains("Monthly payment " + payment + ";")
				.contains("stress-test rate of 8.1%")
				.doesNotContain("suggestAffordableRange");
	}

	@Test
	void livingCostsBelowTheMinimumAreExplained() {
		household("4000");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "200");
		listing("200000", "50");

		assertThat(tools().checkAffordability())
				.contains("Living costs counted: 300 a month (the user said 200, but banks count at least 300 "
						+ "for 1 adult and 0 dependants)");
	}

	@Test
	void tooExpensiveSaysWhyAndPointsToTheRange() {
		household("1500");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "300");
		listing("200000", "50");

		String note = tools().checkAffordability();

		assertThat(note).startsWith("Affordable: no.")
				.contains("Too expensive because: All loan payments would be over 50% of net income")
				.endsWith("Call suggestAffordableRange to tell the user what they could afford instead.");
	}

	@Test
	void theRangeComparesWithTheListingAndItsPricePerSquareMetre() {
		household("1500");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "300");
		listing("200000", "50");
		LoanProfileAssembler.PriceRange range = (LoanProfileAssembler.PriceRange) loans.range(profile.fields(userId),
				loans.terms(banks.typicalBaseMarginPct()));

		String note = tools().suggestAffordableRange();

		assertThat(note)
				.startsWith("Putting in their savings of 50000, the user could afford a home priced up to "
						+ Math.round(range.standardMaxPrice()) + " with a 25-year loan, or up to "
						+ Math.round(range.longestMaxPrice()) + " with a 30-year loan")
				.contains("The home they have in mind costs 200000, "
						+ Math.round(200000 - range.longestMaxPrice()) + " more than the top of that range.")
				.contains("At that listing's price of 4000 per m², the range buys about "
						+ Math.round(range.standardMaxPrice() / 4000) + " to " + Math.round(range.longestMaxPrice() / 4000)
						+ " m²");
	}

	@Test
	void theRangeWorksWithoutAProperty() {
		household("4000");

		assertThat(tools().suggestAffordableRange()).startsWith("Putting in their savings of 50000")
				.doesNotContain("they have in mind");
	}

	@Test
	void theAssumedMarginIsTheBanksMiddleOne() {
		assertThat(banks.typicalBaseMarginPct()).isEqualTo(1.75);
	}
}
