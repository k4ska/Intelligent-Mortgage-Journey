package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.Explanations;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.mockdata.AffordabilityCalculator;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.LoanProfileAssembler;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
			- creditCardLimits: the total limit of all credit cards and overdrafts, used or not, in euros
			- hasCoApplicant: yes or no, whether someone is buying together with the user
			- coApplicantNetIncome: the co-applicant's monthly net income, in euros
			- dependants: number of children or other dependants in the household
			- monthlyLivingCosts: what the household spends a month on living (food, transport,
			  utilities, ...), without loan payments, in euros
			Euro amounts are plain numbers like 2350, with no currency or thousands separators.
			For each fact, give in quote the user's own words that state it, copied exactly
			(e.g. "I work at a bank" for employment=employed). Only save what the user actually
			said; never guess a value. A fact without a real quote, or a number the user never
			wrote, is refused.""";

	static final String UPDATE_PROPERTY_DESCRIPTION = """
			Save what the user told you about the home they have in mind. Allowed keys:
			- type: apartment, house, plot or new_build
			- price: the asking price, in euros
			- location: the address, or at least the town and district
			Euro amounts are plain numbers like 189000, with no currency or thousands separators.
			Size, rooms and year built are only ever read from a listing; don't save them here.
			For each value, give in quote the user's own words that state it, copied exactly from
			their message (or from the listing text they pasted); without one it is refused.
			Set fromPastedText to true when you picked the values out of listing text the user
			pasted: they stay uncertain until the user says they're right, and then you save them
			again with fromPastedText false. Set newProperty to true when the user now has a
			different home in mind; the old one is forgotten.""";

	/** A fact to save: one of the allowed keys, its value, and the user's words that state it. */
	record FieldUpdate(
			@ToolParam(description = "One of the allowed keys") String key,
			@ToolParam(description = "The value to save") String value,
			@ToolParam(description = "The user's own words that state this value, copied exactly from their message")
			String quote) {}

	private final UUID userId;
	private final ProfileService profile;
	private final PropertyService property;
	private final ListingReader listings;
	private final BankCatalogService banks;
	private final LoanProfileAssembler loans;
	private final UserStatements stated;
	private final Consumer<StreamEvent> emit;
	/** Every tool call this turn and what it returned, for the number check (#67). */
	private final List<ToolUse> used = new CopyOnWriteArrayList<>();

	record ToolUse(String name, String result) {}

	AdvisorTools(UUID userId, ProfileService profile, PropertyService property, ListingReader listings,
			BankCatalogService banks, LoanProfileAssembler loans, UserStatements stated, Consumer<StreamEvent> emit) {
		this.userId = userId;
		this.profile = profile;
		this.property = property;
		this.listings = listings;
		this.banks = banks;
		this.loans = loans;
		this.stated = stated;
		this.emit = emit;
	}

	/** Called by the advisor for each tool it ran this turn. */
	void record(String name, String result) {
		used.add(new ToolUse(name, result));
	}

	List<ToolUse> used() {
		return List.copyOf(used);
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
				String value = key.normalize(field.value());
				boolean number = key.type() == ProfileKey.Type.AMOUNT || key.type() == ProfileKey.Type.COUNT;
				if (!stated.containsQuote(field.quote()) || number && !stated.containsNumber(value)) {
					problems.add(notSaid(field.key(), value));
					continue;
				}
				// What the user says about themselves needs no confirmation.
				saved.add(profile.save(userId, key, value, ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED));
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
				String value = key.normalize(field.value());
				if (!stated.containsQuote(field.quote()) || key == PropertyKey.PRICE && !stated.containsNumber(value)) {
					problems.add(notSaid(field.key(), value));
					continue;
				}
				values.put(key, value);
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

	/** A value without the user's words behind it: the advisor guessed it, so it isn't saved (#88). */
	private static String notSaid(String key, String value) {
		return key + "=" + value + " was not saved because the user never said it (quote their exact words); "
				+ "if they haven't told you, ask them instead";
	}

	private static String describe(PropertyView view) {
		return String.join(", ", view.fields().stream().map(f -> f.key() + "=" + f.value()).toList());
	}

	private String stillMissing() {
		List<PropertyKey> missing = property.missing(userId);
		return missing.isEmpty() ? "" : " Still missing: "
				+ String.join(", ", missing.stream().map(PropertyKey::key).toList()) + ". Ask the user about it.";
	}

	@Tool(name = "checkAffordability", description = """
			Check with our affordability rules whether the user can afford the home they have in
			mind. Call it when they ask if they can afford it, or once you know the property and
			their finances; when you save facts in the same step, call it after them. It picks
			the loan term and rate itself. If it lists facts that are missing, ask the user for
			them one at a time; never guess them. Explain the result in plain words using only
			its numbers.""")
	String checkAffordability() {
		List<ProfileFieldView> values = property.active(userId).map(PropertyView::fields).orElse(List.of());
		return switch (loans.check(profile.fields(userId), values, terms())) {
			case LoanProfileAssembler.Missing missing -> cantTellYet(missing);
			case LoanProfileAssembler.Checked checked -> describe(checked);
		};
	}

	@Tool(name = "suggestAffordableRange", description = """
			Work out the price range of homes the user could afford, with our affordability rules.
			Call it when the home they have in mind is too expensive, or when they ask how much
			they could afford. If it lists facts that are missing, ask the user for them one at a
			time. Describe what they could afford (price range, size, or a cheaper area) using
			only its numbers.""")
	String suggestAffordableRange() {
		return switch (loans.range(profile.fields(userId), terms())) {
			case LoanProfileAssembler.Missing missing -> cantTellYet(missing);
			case LoanProfileAssembler.PriceRange range -> describe(range);
		};
	}

	/** The rate the checks assume: the variable rate plus the banks' middle margin, like their offers. */
	private LoanProfileAssembler.Terms terms() {
		return loans.terms(banks.typicalBaseMarginPct());
	}

	private static String cantTellYet(LoanProfileAssembler.Missing missing) {
		StringBuilder note = new StringBuilder("Can't tell yet.");
		if (!missing.missing().isEmpty()) {
			note.append(" Still missing: ").append(String.join(", ", missing.missing())).append('.');
		}
		if (!missing.unconfirmed().isEmpty()) {
			note.append(" Not confirmed by the user yet: ").append(String.join(", ", missing.unconfirmed()))
					.append('.');
		}
		return note.append(" Ask the user about the first of these, one question at a time; don't guess any.")
				.toString();
	}

	private static String describe(LoanProfileAssembler.Checked checked) {
		LoanProfile profile = checked.profile();
		LoanProfile.Financials money = profile.financials();
		LoanProfile.Household household = profile.household();
		double stressedRate = AffordabilityCalculator.stressedRate(checked.terms().ratePct());

		StringBuilder note = new StringBuilder();
		note.append("Affordable: ").append(checked.affordable() ? "yes" : "no").append(". ");
		note.append("Price ").append(euros(profile.property().purchasePrice()))
				.append(", down payment ").append(euros(money.downPayment()))
				.append(", loan ").append(euros(profile.loanRequest().amount()))
				.append(" (loan-to-value ").append(number(money.ltv())).append("%, at most ")
				.append(number(AffordabilityCalculator.TYPICAL_MAX_LTV_PCT)).append("%). ");
		note.append(rate(checked.terms(), profile.loanRequest().termYears()));
		note.append("Monthly payment ").append(euros(money.estimatedMonthlyPayment()))
				.append("; at the stress-test rate of ").append(number(stressedRate)).append("% it would be ")
				.append(euros(money.stressedMonthlyPayment())).append(". ");
		note.append("All loan payments incl. this one take ").append(number(money.dti()))
				.append("% of net income, and ").append(number(money.stressedDti()))
				.append("% at the stress-test rate (at most ").append(number(AffordabilityCalculator.MAX_DTI_PCT))
				.append("%). ");
		if (money.creditLimitMonthlyCost() > 0) {
			note.append("Credit card limits count as ").append(euros(money.creditLimitMonthlyCost()))
					.append(" a month of those payments. ");
		}
		note.append("Living costs counted: ").append(euros(money.monthlyLivingCosts())).append(" a month");
		if (household.enteredMonthlyCosts() != null && household.enteredMonthlyCosts() < money.monthlyLivingCosts()) {
			note.append(" (the user said ").append(euros(household.enteredMonthlyCosts()))
					.append(", but banks count at least ").append(euros(household.minimumMonthlyCosts()))
					.append(" for ").append(plural(household.adults(), "adult"))
					.append(" and ").append(plural(household.dependants(), "dependant")).append(')');
		}
		note.append(". Left each month after debts, living costs and the stressed payment: ")
				.append(euros(money.monthlyBufferAfterStress())).append(" (at least ")
				.append(euros(AffordabilityCalculator.MIN_STRESS_BUFFER)).append(" needed). ");
		note.append("Largest loan the rules allow: ").append(euros(checked.maxLoan().amount())).append('.');
		if (!checked.affordable()) {
			note.append(" Too expensive because: ").append(String.join("; ", profile.riskIndicators()))
					.append(". Call suggestAffordableRange to tell the user what they could afford instead.");
		}
		return note.toString();
	}

	private String describe(LoanProfileAssembler.PriceRange range) {
		StringBuilder note = new StringBuilder();
		if (range.longest().amount() <= 0) {
			note.append("The user can't borrow anything yet. What limits it: ")
					.append(limitedBy(range.longest().limitedBy())).append(". ");
		} else {
			note.append("Putting in their savings of ").append(euros(range.savings()))
					.append(", the user could afford a home priced up to ").append(euros(range.standardMaxPrice()))
					.append(" with a ").append(LoanProfileAssembler.TERM_YEARS).append("-year loan");
			if (range.longestMaxPrice() > range.standardMaxPrice()) {
				note.append(", or up to ").append(euros(range.longestMaxPrice())).append(" with a ")
						.append(LoanProfileAssembler.MAX_TERM_YEARS).append("-year loan (the longest allowed)");
			}
			note.append(". What limits it: ").append(limitedBy(range.longest().limitedBy())).append(". ");
		}
		note.append(rate(range.terms(), LoanProfileAssembler.TERM_YEARS));
		if (range.longest().amount() > 0) {
			property.active(userId).ifPresent(home -> note.append(compared(home, range)));
		}
		return note.toString().trim();
	}

	/** How the range compares with the user's property, and the size it buys at that listing's price per m². */
	private static String compared(PropertyView home, LoanProfileAssembler.PriceRange range) {
		Double price = confirmedNumber(home, PropertyKey.PRICE);
		if (price == null) {
			return "";
		}
		double gap = price - range.longestMaxPrice();
		StringBuilder note = new StringBuilder("The home they have in mind costs ").append(euros(price)).append(", ")
				.append(euros(Math.abs(gap))).append(gap > 0 ? " more" : " less").append(" than the top of that range. ");
		Double size = confirmedNumber(home, PropertyKey.SIZE);
		if (size != null && size > 0) {
			double perSquareMetre = price / size;
			note.append("At that listing's price of ").append(euros(perSquareMetre))
					.append(" per m², the range buys about ").append(Math.round(range.standardMaxPrice() / perSquareMetre));
			if (range.longestMaxPrice() > range.standardMaxPrice()) {
				note.append(" to ").append(Math.round(range.longestMaxPrice() / perSquareMetre));
			}
			note.append(" m² in a similar area.");
		}
		return note.toString();
	}

	private static Double confirmedNumber(PropertyView home, PropertyKey key) {
		return home.fields().stream()
				.filter(f -> f.key().equals(key.key()) && f.status() == ProfileField.Status.CONFIRMED)
				.findFirst()
				.map(f -> {
					try {
						return Double.valueOf(f.value());
					} catch (NumberFormatException e) {
						return null;
					}
				})
				.orElse(null);
	}

	private static String rate(LoanProfileAssembler.Terms terms, int termYears) {
		return "Assumed: " + termYears + " years at " + number(terms.ratePct()) + "% a year (the variable rate of "
				+ number(terms.variableRatePct()) + "% plus a typical bank margin of " + number(terms.marginPct())
				+ "%). ";
	}

	private static String limitedBy(AffordabilityCalculator.LoanLimit limit) {
		return switch (limit) {
			case INCOME -> "income: all loan payments may take at most "
					+ number(AffordabilityCalculator.MAX_DTI_PCT) + "% of net income at the stress-test rate";
			case STRESS -> "what is left each month after debts and living costs at the stress-test rate";
			case DOWN_PAYMENT -> "the down payment: savings must cover at least "
					+ number(100 - AffordabilityCalculator.TYPICAL_MAX_LTV_PCT) + "% of the price";
		};
	}

	private static String plural(int count, String noun) {
		return count + " " + noun + (count == 1 ? "" : "s");
	}

	/** Whole euros, as a plain number like the saved values. */
	private static String euros(double amount) {
		return Long.toString(Math.round(amount));
	}

	private static String number(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	@Tool(name = "explainTerm", description = """
			Look up what a mortgage term means in our glossary, e.g. Euribor, margin, loan-to-value,
			stress test, down payment, valuation. Call it whenever the user asks what a term means
			or how something works, and base your explanation on the entry it returns.""")
	String explainTerm(@ToolParam(description = "The term, e.g. \"Euribor\" or \"loan-to-value\"") String topic) {
		return Explanations.find(topic)
				.map(found -> "Glossary entry for " + found.key() + ": " + found.entry().text()
						+ (found.entry().related().isEmpty() ? "" : " Related topics: "
								+ String.join(", ", found.entry().related()) + ".")
						+ " Explain it in your own plain words, based only on this entry.")
				.orElseGet(() -> "\"" + topic + "\" is not in the glossary. Topics it has: "
						+ String.join(", ", Explanations.topics()) + ". If one of them is what the user means, "
						+ "look that up instead. Otherwise keep your answer short and general, say it is general "
						+ "information, and don't state any figures or rules as facts.");
	}

	@Tool(name = "showBankPicker", description = """
			Show the user a card where they pick the banks to send their application to. Call it
			only when the user has asked to apply or go to the banks, never on your own. If the
			user isn't ready yet, this tells you what is still missing; ask the user for it instead.""")
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
