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
import com.seb.mortgage.mockdata.BankCatalogService;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;

/** The advisor's tools, called directly: no LLM involved. */
@SpringBootTest
class AdvisorToolsTest {

	@Autowired ProfileService profile;
	@Autowired BankCatalogService banks;

	private final UUID userId = UUID.randomUUID();
	private final List<StreamEvent> events = new ArrayList<>();

	private AdvisorTools tools() {
		return new AdvisorTools(userId, profile, banks, events::add);
	}

	@Test
	void updateProfileSavesWhatTheUserSaidAndTellsThePanel() {
		String note = tools().updateProfile(List.of(new FieldUpdate("netIncome", "2350")));

		ProfileFieldView expected = new ProfileFieldView("netIncome", "2350",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		assertThat(profile.fields(userId)).containsExactly(expected);
		assertThat(events).containsExactly(StreamEvent.profileUpdated(List.of(expected)));
		assertThat(note).isEqualTo("Saved: netIncome=2350.");
	}

	@Test
	void aNewValueReplacesTheOldOne() {
		tools().updateProfile(List.of(new FieldUpdate("netIncome", "2350")));
		tools().updateProfile(List.of(new FieldUpdate("netIncome", "2400")));

		assertThat(profile.fields(userId)).extracting(ProfileFieldView::value).containsExactly("2400");
	}

	@Test
	void unknownKeysAndBadValuesAreRejectedWithAReason() {
		String note = tools().updateProfile(List.of(
				new FieldUpdate("salary", "2350"),
				new FieldUpdate("savings", "lots"),
				new FieldUpdate("dependants", "2")));

		assertThat(profile.fields(userId)).extracting(ProfileFieldView::key).containsExactly("dependants");
		assertThat(note).startsWith("Saved: dependants=2.")
				.contains("salary is not an allowed key")
				.contains("savings must be a plain number");
	}

	@Test
	void nothingValidMeansNoEvent() {
		assertThat(tools().updateProfile(List.of(new FieldUpdate("salary", "1")))).startsWith("Not saved:");
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
		tools().updateProfile(List.of(new FieldUpdate("netIncome", "2350")));

		assertThat(profile.fields(UUID.randomUUID())).isEmpty();
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
