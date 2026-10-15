package com.seb.mortgage.advisor;

import com.seb.mortgage.mockdata.BankCatalogService;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The advisor's tools for one turn of one user's conversation. Each tool runs our code,
 * may push an event for the panels, and returns a short note the advisor reads. A new
 * instance is made per turn, so a tool can only ever act for the user who's talking.
 */
class AdvisorTools {

	static final String UPDATE_PROFILE_DESCRIPTION = """
			Save facts the user told you about themselves to their profile, as soon as they say
			them. Allowed keys:
			- netIncome: monthly net income after tax, in euros
			- employment: employment type, e.g. employed, self-employed, student, retired
			- savings: savings available for the down payment, in euros
			- monthlyDebtPayments: monthly payments on other loans and leases, in euros
			- hasCoApplicant: yes or no, whether someone is buying together with the user
			- coApplicantNetIncome: the co-applicant's monthly net income, in euros
			- dependants: number of children or other dependants in the household
			Euro amounts are plain numbers like 2350, with no currency or thousands separators.
			Only save what the user actually said; never guess a value.""";

	/** A fact to save: one of the allowed keys and its value. */
	record FieldUpdate(String key, String value) {}

	private final UUID userId;
	private final ProfileService profile;
	private final BankCatalogService banks;
	private final Consumer<StreamEvent> emit;

	AdvisorTools(UUID userId, ProfileService profile, BankCatalogService banks, Consumer<StreamEvent> emit) {
		this.userId = userId;
		this.profile = profile;
		this.banks = banks;
		this.emit = emit;
	}

	@Tool(name = "updateProfile", description = UPDATE_PROFILE_DESCRIPTION)
	String updateProfile(@ToolParam(description = "The facts to save") List<FieldUpdate> fields) {
		List<ProfileFieldView> saved = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (FieldUpdate field : fields == null ? List.<FieldUpdate>of() : fields) {
			ProfileKey key = ProfileKey.fromKey(field.key()).orElse(null);
			if (key == null) {
				problems.add(field.key() + " is not an allowed key");
				continue;
			}
			try {
				// What the user says about themselves needs no confirmation.
				saved.add(profile.save(userId, key, key.normalize(field.value()),
						ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED));
			} catch (IllegalArgumentException e) {
				problems.add(e.getMessage());
			}
		}
		if (!saved.isEmpty()) {
			emit.accept(StreamEvent.profileUpdated(saved));
		}

		StringBuilder note = new StringBuilder();
		if (!saved.isEmpty()) {
			note.append("Saved: ")
					.append(String.join(", ", saved.stream().map(f -> f.key() + "=" + f.value()).toList()))
					.append(". ");
		}
		if (!problems.isEmpty()) {
			note.append("Not saved: ").append(String.join("; ", problems)).append('.');
		}
		return note.isEmpty() ? "Nothing to save." : note.toString().trim();
	}

	@Tool(name = "showBankPicker", description = """
			Show the user a card where they pick the banks to send their application to. Call it
			when the user wants to go to the bank. If the user isn't ready yet, this tells you what
			is still missing; ask the user for it instead.""")
	String showBankPicker() {
		// Skeleton readiness rule (#50): the real checkReadiness comes later.
		if (!profile.has(userId, ProfileKey.NET_INCOME)) {
			return "Not ready yet: the user's monthly net income is still missing. Ask for it first. "
					+ "The bank picker was not shown.";
		}
		emit.accept(StreamEvent.bankPicker(banks.banks().stream()
				.map(bank -> new StreamEvent.BankOption(bank.id(), bank.name()))
				.toList()));
		return "The bank picker is now shown under the chat. Tell the user they can tick one or more "
				+ "banks there and send the application.";
	}
}
