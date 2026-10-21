package com.seb.mortgage.advisor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seb.mortgage.bank.ApplicationRepository;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.documents.Document;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentType;
import com.seb.mortgage.documents.DocumentView;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;

/**
 * Works out the conversation's {@link Phase} and whether the user is ready for the banks
 * (#73), in plain code from the stored data only.
 */
@Service
public class Phases {

	/** The facts the advisor asks about first; without them it's still getting to know the user. */
	static final List<ProfileKey> KEY_FACTS = List.of(ProfileKey.NET_INCOME, ProfileKey.SAVINGS,
			ProfileKey.HAS_CO_APPLICANT, ProfileKey.MONTHLY_DEBT_PAYMENTS);

	/**
	 * The documents a bank needs before it makes an offer. The valuation and the purchase
	 * agreement come later: banks make their offer on condition of them.
	 */
	static final List<DocumentType> REQUIRED_DOCUMENTS = List.of(DocumentType.ID, DocumentType.PAYSLIP,
			DocumentType.BANK_STATEMENT, DocumentType.DOWN_PAYMENT_PROOF, DocumentType.CO_APPLICANT_ID,
			DocumentType.CO_APPLICANT_PAYSLIP);

	/**
	 * What {@link #readiness} found: ready, or what is still missing in the order the advisor
	 * should ask for it.
	 *
	 * @param facts     profile facts that are missing or not confirmed, by profile key
	 * @param property  property values that are missing or not confirmed, or "a property" without one
	 * @param documents required documents that aren't confirmed yet
	 */
	public record Readiness(List<String> facts, List<String> property, List<DocumentView> documents) {
		public boolean ready() {
			return facts.isEmpty() && property.isEmpty() && documents.isEmpty();
		}

		/** What is missing, for the advisor to read; empty when ready. */
		public String describe() {
			List<String> parts = new ArrayList<>();
			if (!facts.isEmpty()) {
				parts.add("profile facts (missing or not confirmed): " + String.join(", ", facts));
			}
			if (!property.isEmpty()) {
				parts.add("property: " + String.join(", ", property));
			}
			if (!documents.isEmpty()) {
				parts.add("documents: " + String.join(", ", documents.stream()
						.map(d -> d.type() + " (" + statusText(d.status()) + ")").toList()));
			}
			return String.join("; ", parts);
		}

		private static String statusText(Document.Status status) {
			return switch (status) {
				case NOT_NEEDED_YET -> "not asked for yet";
				case NEEDED -> "not uploaded yet";
				case UPLOADED -> "uploaded, values not confirmed yet";
				case CONFIRMED -> "confirmed";
			};
		}
	}

	private final ProfileService profile;
	private final PropertyService property;
	private final DocumentService documents;
	private final ConversationService conversations;
	private final ApplicationRepository applications;
	private final LoanProfileAssembler loans;
	private final BankCatalogService banks;

	public Phases(ProfileService profile, PropertyService property, DocumentService documents,
			ConversationService conversations, ApplicationRepository applications, LoanProfileAssembler loans,
			BankCatalogService banks) {
		this.profile = profile;
		this.property = property;
		this.documents = documents;
		this.conversations = conversations;
		this.applications = applications;
		this.loans = loans;
		this.banks = banks;
	}

	/** The phase, and what is still missing before the banks. */
	public record Current(Phase phase, Readiness readiness) {
		/** What the advisor is told about where the conversation is, each time it acts and replies. */
		public String note() {
			StringBuilder note = new StringBuilder("Where the conversation is now: ").append(phase.label())
					.append(". Focus: ").append(phase.focus());
			if (!readiness.ready() && phase != Phase.AT_THE_BANKS) {
				note.append(" The user can't go to the banks yet. If they ask to, don't offer it; tell them "
						+ "kindly what is still needed and carry on. Still needed: ").append(readiness.describe())
						.append('.');
			}
			return note.toString();
		}
	}

	public Current current(UUID userId) {
		Readiness readiness = readiness(userId);
		return new Current(phase(userId, readiness), readiness);
	}

	public Phase phase(UUID userId) {
		return current(userId).phase();
	}

	/** The advisor gave the user an affordability result: from now on it asks for documents. */
	public void affordabilityDiscussed(UUID userId) {
		conversations.affordabilityDiscussed(userId);
	}

	private Phase phase(UUID userId, Readiness readiness) {
		if (applications.existsByUserId(userId)) {
			return Phase.AT_THE_BANKS;
		}
		if (readiness.ready()) {
			return Phase.READY;
		}
		boolean documentsAskedFor = documents.checklist(userId).stream()
				.anyMatch(document -> document.status() != Document.Status.NOT_NEEDED_YET);
		if (conversations.hasDiscussedAffordability(userId) || documentsAskedFor) {
			return Phase.DOCUMENTS;
		}
		if (KEY_FACTS.stream().anyMatch(key -> !profile.has(userId, key))) {
			return Phase.GETTING_TO_KNOW_YOU;
		}
		return property.active(userId).isEmpty() ? Phase.PROPERTY : Phase.AFFORDABILITY;
	}

	/**
	 * Are all the facts the affordability check needs, the property and the required
	 * documents confirmed? A value read from a document counts only once the user confirmed it.
	 */
	public Readiness readiness(UUID userId) {
		List<String> facts = switch (loans.range(profile.fields(userId), loans.terms(banks.typicalBaseMarginPct()))) {
			case LoanProfileAssembler.Missing missing -> {
				Set<String> keys = new LinkedHashSet<>(missing.missing());
				keys.addAll(missing.unconfirmed());
				yield List.copyOf(keys);
			}
			case LoanProfileAssembler.PriceRange range -> List.of();
		};

		List<String> home = new ArrayList<>();
		PropertyView active = property.active(userId).orElse(null);
		if (active == null) {
			home.add("a property");
		} else {
			property.missing(userId).stream().map(PropertyKey::key).forEach(home::add);
			active.fields().stream()
					.filter(field -> PropertyKey.fromKey(field.key()).map(PropertyKey::required).orElse(false))
					.filter(field -> field.status() != ProfileField.Status.CONFIRMED)
					.forEach(field -> home.add(field.key() + " (not confirmed)"));
		}

		List<DocumentView> notConfirmed = documents.checklist(userId).stream()
				.filter(document -> REQUIRED_DOCUMENTS.contains(document.type()))
				.filter(document -> documents.applies(userId, document.type()))
				.filter(document -> document.status() != Document.Status.CONFIRMED)
				.toList();
		return new Readiness(facts, home, notConfirmed);
	}
}
