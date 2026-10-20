package com.seb.mortgage.advisor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.advisor.AdvisorTools.FieldUpdate;
import com.seb.mortgage.agent.Explanations;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;

/** The advisor's tools, called directly: no LLM involved. */
@SpringBootTest
class AdvisorToolsTest {

	@Autowired ProfileService profile;
	@Autowired BankCatalogService banks;
	@Autowired PropertyService property;
	@Autowired ListingReader listings;
	@Autowired LoanProfileAssembler loans;

	private final UUID userId = UUID.randomUUID();
	private final List<StreamEvent> events = new ArrayList<>();
	/** What the user wrote this turn; tools only save numbers that appear in it. */
	private String userSaid = "I earn 2350 a month, soon 2400, and we have 2 kids";

	private AdvisorTools tools() {
		return new AdvisorTools(userId, profile, property, listings, banks, loans,
				UserStatements.from(List.of(), userSaid), events::add);
	}

	/** A fact the user stated in {@link #userSaid}, quoting their whole message. */
	private FieldUpdate field(String key, String value) {
		return new FieldUpdate(key, value, userSaid);
	}

	@Test
	void updateProfileSavesWhatTheUserSaidAndTellsThePanel() {
		String note = tools().updateProfile(List.of(field("netIncome", "2350")));

		ProfileFieldView expected = new ProfileFieldView("netIncome", "2350",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		assertThat(profile.fields(userId)).containsExactly(expected);
		assertThat(events).containsExactly(StreamEvent.profileUpdated(List.of(expected)));
		assertThat(note).isEqualTo("Saved: netIncome=2350.");
	}

	@Test
	void aNewValueReplacesTheOldOne() {
		tools().updateProfile(List.of(field("netIncome", "2350")));
		tools().updateProfile(List.of(field("netIncome", "2400")));

		assertThat(profile.fields(userId)).extracting(ProfileFieldView::value).containsExactly("2400");
	}

	@Test
	void unknownKeysAndBadValuesAreRejectedWithAReason() {
		String note = tools().updateProfile(List.of(
				field("salary", "2350"),
				field("savings", "lots"),
				field("dependants", "2")));

		assertThat(profile.fields(userId)).extracting(ProfileFieldView::key).containsExactly("dependants");
		assertThat(note).startsWith("Saved: dependants=2.")
				.contains("salary is not an allowed key")
				.contains("savings must be a plain number");
	}

	/** #88: the advisor saved savings=0 and netIncome=3300 when the user had only described a house. */
	@Test
	void numbersTheUserNeverWroteAreNotSaved() {
		userSaid = "It's a house at Kaare tn 3, Rae vald, for 478000";

		String note = tools().updateProfile(List.of(field("savings", "0"), field("netIncome", "3300")));

		assertThat(profile.fields(userId)).isEmpty();
		assertThat(events).isEmpty();
		assertThat(note).startsWith("Not saved:")
				.contains("savings=0 was not saved because the user never said it")
				.contains("netIncome=3300 was not saved");
	}

	@Test
	void aNoAnswerCountsAsZeroOnlyRightAfterTheQuestion() {
		userSaid = "No, I don't have any other loans";

		assertThat(tools().updateProfile(List.of(field("monthlyDebtPayments", "0")))).startsWith("Saved:");
	}

	@Test
	void aTextValueIsSavedWithTheUsersOwnWords() {
		userSaid = "I work at a bank";

		assertThat(tools().updateProfile(List.of(new FieldUpdate("employment", "employed", "I work at a bank!"))))
				.startsWith("Saved:");
	}

	/** Testing #90: "a house for 690 000" made the advisor save employment=employed. */
	@Test
	void aTextValueWithAMadeUpOrMissingQuoteIsNotSaved() {
		userSaid = "It's a house with a price of 690 000";

		String note = tools().updateProfile(List.of(new FieldUpdate("employment", "employed", "I'm employed"),
				new FieldUpdate("hasCoApplicant", "no", null)));

		assertThat(note).startsWith("Not saved:").contains("employment=employed was not saved")
				.contains("hasCoApplicant=no was not saved");
		assertThat(profile.fields(userId)).isEmpty();
	}

	@Test
	void nothingValidMeansNoEvent() {
		assertThat(tools().updateProfile(List.of(field("salary", "1")))).startsWith("Not saved:");
		assertThat(tools().updateProfile(null)).isEqualTo("Nothing to save.");
		assertThat(events).isEmpty();
	}

	@Test
	void bankPickerWaitsForTheIncome() {
		String note = tools().showBankPicker();

		assertThat(note).startsWith("Not ready yet").contains("net income");
		assertThat(events).isEmpty();
	}

	@Test
	void bankPickerShowsEveryBankOnceTheIncomeIsKnown() {
		profile.save(userId, ProfileKey.NET_INCOME, "2350", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);

		String note = tools().showBankPicker();

		assertThat(note).contains("bank picker is now shown");
		assertThat(events).containsExactly(StreamEvent.bankPicker(banks.banks().stream()
				.map(bank -> new StreamEvent.BankOption(bank.id(), bank.name()))
				.toList()));
	}

	@Test
	void eachToolActsOnlyForItsOwnUser() {
		tools().updateProfile(List.of(field("netIncome", "2350")));

		assertThat(profile.fields(UUID.randomUUID())).isEmpty();
	}

	/** #66: definitions come from our glossary, not the model's memory. */
	@Test
	void explainTermReturnsTheGlossaryEntry() {
		String note = tools().explainTerm("LTV");

		assertThat(note).startsWith("Glossary entry for loan-to-value: " + Explanations.lookup("loan-to-value").text())
				.contains("Related topics: down-payment, interest-rate, stress-test.")
				.endsWith("based only on this entry.");
		assertThat(events).isEmpty();
	}

	@Test
	void explainTermSaysWhenATermIsNotInTheGlossary() {
		String note = tools().explainTerm("balloon payment");

		assertThat(note).startsWith("\"balloon payment\" is not in the glossary. Topics it has: affordability, amortization,")
				.contains("don't state any figures or rules as facts");
	}

	/** The tool description is what the advisor knows about the keys, so it must list all of them. */
	@Test
	void updateProfileDescribesEveryAllowedKey() throws NoSuchMethodException {
		Method method = AdvisorTools.class.getDeclaredMethod("updateProfile", List.class);
		String description = method.getAnnotation(Tool.class).description();

		for (ProfileKey key : ProfileKey.values()) {
			assertThat(description).contains("- " + key.key() + ":");
		}
	}
}
