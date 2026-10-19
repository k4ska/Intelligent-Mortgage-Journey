package com.seb.mortgage.advisor;

import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

	static final String UPDATE_PROPERTY_DESCRIPTION = """
			Save what the user told you about the home they have in mind. Allowed keys:
			- type: apartment, house, plot or new_build
			- price: the asking price, in euros
			- location: the address, or at least the town and district
			Euro amounts are plain numbers like 189000, with no currency or thousands separators.
			Size, rooms and year built are only ever read from a listing; don't save them here.
			Set fromPastedText to true when you picked the values out of listing text the user
			pasted: they stay uncertain until the user says they're right, and then you save them
			again with fromPastedText false. Set newProperty to true when the user now has a
			different home in mind; the old one is forgotten.""";

	/** A fact to save: one of the allowed keys and its value. */
	record FieldUpdate(String key, String value) {}

	private final UUID userId;
	private final ProfileService profile;
	private final PropertyService property;
	private final ListingReader listings;
	private final BankCatalogService banks;
	private final Consumer<StreamEvent> emit;

	AdvisorTools(UUID userId, ProfileService profile, PropertyService property, ListingReader listings,
			BankCatalogService banks, Consumer<StreamEvent> emit) {
		this.userId = userId;
		this.profile = profile;
		this.property = property;
		this.listings = listings;
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

	@Tool(name = "lookupListing", description = """
			Read a kv.ee listing the user pasted a link to, and save it as the home they have in
			mind (it replaces any earlier one). Call it once per link. If it says the page
			couldn't be read, don't try again; ask the user instead.""")
	String lookupListing(@ToolParam(description = "The kv.ee link, as the user pasted it") String url) {
		return switch (listings.read(url)) {
			case ListingReader.Lookup.NotRead notRead -> "Couldn't read the listing: " + notRead.reason()
					+ ". Don't try again. Ask the user for the property type (apartment, house, plot or new "
					+ "build), the price and the location, or to paste the listing's text.";
			case ListingReader.Lookup.Found found -> {
				PropertyView saved = property.replaceFromListing(userId, found.url(), found.values());
				emit.accept(StreamEvent.propertyUpdated(saved));
				yield "Read the listing and saved: " + describe(saved) + "." + stillMissing();
			}
		};
	}

	@Tool(name = "updateProperty", description = UPDATE_PROPERTY_DESCRIPTION)
	String updateProperty(@ToolParam(description = "The values to save") List<FieldUpdate> fields,
			@ToolParam(description = "True if you picked the values out of pasted listing text", required = false)
			Boolean fromPastedText,
			@ToolParam(description = "True if this is a different home than the one saved before", required = false)
			Boolean newProperty) {
		Map<PropertyKey, String> values = new EnumMap<>(PropertyKey.class);
		List<String> problems = new ArrayList<>();
		for (FieldUpdate field : fields == null ? List.<FieldUpdate>of() : fields) {
			PropertyKey key = PropertyKey.fromKey(field.key()).orElse(null);
			if (key == null || !key.required()) {
				problems.add(field.key() + (key == null ? " is not an allowed key" : " is only read from a listing"));
				continue;
			}
			try {
				values.put(key, key.normalize(field.value()));
			} catch (IllegalArgumentException e) {
				problems.add(e.getMessage());
			}
		}

		StringBuilder note = new StringBuilder();
		if (!values.isEmpty()) {
			boolean uncertain = Boolean.TRUE.equals(fromPastedText);
			PropertyView saved = property.updateFromChat(userId, values,
					uncertain ? ProfileField.Status.UNCERTAIN : ProfileField.Status.CONFIRMED,
					Boolean.TRUE.equals(newProperty));
			emit.accept(StreamEvent.propertyUpdated(saved));
			note.append("Saved").append(uncertain ? " as uncertain" : "").append(": ")
					.append(String.join(", ", values.entrySet().stream()
							.map(e -> e.getKey().key() + "=" + e.getValue()).toList()))
					.append(". ");
			if (uncertain) {
				note.append("Read these back to the user and ask if they're right. ");
			}
		}
		if (!problems.isEmpty()) {
			note.append("Not saved: ").append(String.join("; ", problems)).append(". ");
		}
		if (note.isEmpty()) {
			return "Nothing to save.";
		}
		return note.toString().trim() + stillMissing();
	}

	private static String describe(PropertyView view) {
		return String.join(", ", view.fields().stream().map(f -> f.key() + "=" + f.value()).toList());
	}

	private String stillMissing() {
		List<PropertyKey> missing = property.missing(userId);
		return missing.isEmpty() ? "" : " Still missing: "
				+ String.join(", ", missing.stream().map(PropertyKey::key).toList()) + ". Ask the user about it.";
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
