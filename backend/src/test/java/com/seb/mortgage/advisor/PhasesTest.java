package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.bank.Application;
import com.seb.mortgage.bank.ApplicationRepository;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.documents.Document;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentType;
import com.seb.mortgage.documents.DocumentView;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;

/** #73: the conversation's phase and the readiness check, worked out from the stored data only. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class PhasesTest {

	@Autowired Phases phases;
	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired DocumentService documents;
	@Autowired ApplicationRepository applications;
	@Autowired ListingReader listings;
	@Autowired BankCatalogService banks;
	@Autowired LoanProfileAssembler loans;

	private final UUID userId = UUID.randomUUID();
	private final List<StreamEvent> events = new ArrayList<>();

	// ---- One test per phase ----

	@Test
	void aNewUserIsBeingGotToKnow() {
		assertThat(phases.phase(userId)).isEqualTo(Phase.GETTING_TO_KNOW_YOU);
	}

	@Test
	void someKeyFactsAreNotEnough() {
		save(ProfileKey.NET_INCOME, "3200");
		save(ProfileKey.SAVINGS, "40000");
		save(ProfileKey.HAS_CO_APPLICANT, "no");

		assertThat(phases.phase(userId)).isEqualTo(Phase.GETTING_TO_KNOW_YOU);
	}

	@Test
	void withTheKeyFactsItAsksAboutTheProperty() {
		keyFacts();

		assertThat(phases.phase(userId)).isEqualTo(Phase.PROPERTY);
	}

	@Test
	void withAPropertyItChecksAffordability() {
		keyFacts();
		home();

		assertThat(phases.phase(userId)).isEqualTo(Phase.AFFORDABILITY);
	}

	@Test
	void onceAffordabilityWasDiscussedItAsksForDocuments() {
		keyFacts();
		home();
		phases.affordabilityDiscussed(userId);

		assertThat(phases.phase(userId)).isEqualTo(Phase.DOCUMENTS);
	}

	@Test
	void aDocumentAlreadyAskedForAlsoMeansTheDocumentsPhase() {
		documents.request(userId, DocumentType.ID);

		assertThat(phases.phase(userId)).isEqualTo(Phase.DOCUMENTS);
	}

	@Test
	void withEverythingConfirmedTheUserIsReady() {
		ready();

		assertThat(phases.phase(userId)).isEqualTo(Phase.READY);
	}

	@Test
	void afterTheApplicationIsSentTheUserIsAtTheBanks() {
		ready();
		applications.save(new Application(userId, "{}", "[\"seb\"]"));

		assertThat(phases.phase(userId)).isEqualTo(Phase.AT_THE_BANKS);
	}

	@Test
	void toolsUnlockCumulatively() {
		assertThat(Phase.GETTING_TO_KNOW_YOU.tools())
				.containsExactly("updateProfile", "lookupListing", "updateProperty", "explainTerm");
		assertThat(Phase.AFFORDABILITY.tools()).isEqualTo(Phase.PROPERTY.tools())
				.contains("updateProfile", "checkAffordability", "suggestAffordableRange")
				.doesNotContain("requestDocument");
		assertThat(Phase.DOCUMENTS.tools()).contains("updateProfile", "checkAffordability", "requestDocument",
				"confirmDocument", "checkReadiness").doesNotContain("showBankPicker");
		assertThat(Phase.READY.tools()).contains("showBankPicker");
		assertThat(Phase.AT_THE_BANKS.tools()).isEqualTo(Phase.READY.tools());
	}

	// ---- checkReadiness ----

	@Test
	void aNewUserMissesEverything() {
		Phases.Readiness readiness = phases.readiness(userId);

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.facts()).contains("netIncome", "savings", "hasCoApplicant", "monthlyDebtPayments");
		assertThat(readiness.property()).containsExactly("a property");
		assertThat(readiness.documents()).extracting(DocumentView::type).containsExactly(DocumentType.ID,
				DocumentType.PAYSLIP, DocumentType.BANK_STATEMENT, DocumentType.DOWN_PAYMENT_PROOF);
		assertThat(readiness.describe()).contains("ID (not asked for yet)");
	}

	@Test
	void theValuationAndPurchaseAgreementAreNotNeededBeforeTheBanks() {
		ready();

		assertThat(phases.readiness(userId).ready()).isTrue();
		assertThat(documents.checklist(userId)).filteredOn(d -> d.type() == DocumentType.VALUATION)
				.extracting(DocumentView::status).containsExactly(Document.Status.NOT_NEEDED_YET);
	}

	@Test
	void aValueReadFromADocumentCountsOnlyOnceConfirmed() {
		ready();
		profile.saveFromDocument(userId, ProfileKey.NET_INCOME, "3150", "PAYSLIP");

		Phases.Readiness readiness = phases.readiness(userId);

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.facts()).containsExactly("netIncome");
	}

	@Test
	void anUploadedDocumentCountsOnlyOnceConfirmed() {
		ready();
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();
		documents.recordReading(userId, payslip, Map.of("netIncome", "3200"));

		Phases.Readiness readiness = phases.readiness(userId);

		assertThat(readiness.documents()).extracting(DocumentView::type, DocumentView::status)
				.containsExactly(tuple(DocumentType.PAYSLIP, Document.Status.UPLOADED));
		assertThat(readiness.describe()).contains("PAYSLIP (uploaded, values not confirmed yet)");
	}

	@Test
	void anUnconfirmedPropertyValueIsNotReady() {
		ready();
		property.updateFromChat(userId, Map.of(PropertyKey.PRICE, "180000"), ProfileField.Status.UNCERTAIN, false);

		assertThat(phases.readiness(userId).property()).containsExactly("price (not confirmed)");
	}

	@Test
	void aCoApplicantNeedsTheirOwnIncomeAndDocuments() {
		ready();
		save(ProfileKey.HAS_CO_APPLICANT, "yes");

		Phases.Readiness withoutTheirs = phases.readiness(userId);
		assertThat(withoutTheirs.facts()).containsExactly("coApplicantNetIncome");
		assertThat(withoutTheirs.documents()).extracting(DocumentView::type)
				.containsExactly(DocumentType.CO_APPLICANT_ID, DocumentType.CO_APPLICANT_PAYSLIP);

		save(ProfileKey.CO_APPLICANT_NET_INCOME, "2100");
		confirmDocument(DocumentType.CO_APPLICANT_ID);
		confirmDocument(DocumentType.CO_APPLICANT_PAYSLIP);

		assertThat(phases.readiness(userId).ready()).isTrue();
	}

	@Test
	void coApplicantDocumentsDontCountWithoutACoApplicant() {
		save(ProfileKey.HAS_CO_APPLICANT, "yes");
		confirmDocument(DocumentType.CO_APPLICANT_ID);
		// The key facts say "no" co-applicant now.
		ready();

		assertThat(phases.readiness(userId).ready()).isTrue();
	}

	// ---- The tools on top of it ----

	@Test
	void theBankPickerWaitsUntilTheUserIsReady() {
		keyFacts();

		String note = tools().showBankPicker();

		assertThat(note).startsWith("Not ready yet").contains("a property").contains("PAYSLIP (not asked for yet)");
		assertThat(events).isEmpty();
		assertThat(tools().checkReadiness()).startsWith("Not ready yet. Still needed:");
	}

	@Test
	void theBankPickerShowsEveryBankOnceReady() {
		ready();

		assertThat(tools().checkReadiness()).startsWith("Ready");
		assertThat(tools().showBankPicker()).contains("bank picker is now shown");
		assertThat(events).containsExactly(StreamEvent.bankPicker(banks.banks().stream()
				.map(bank -> new StreamEvent.BankOption(bank.id(), bank.name()))
				.toList()));
	}

	@Test
	void anAffordabilityResultMovesTheConversationOn() {
		keyFacts();
		save(ProfileKey.CREDIT_CARD_LIMITS, "0");
		save(ProfileKey.DEPENDANTS, "0");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "900");
		home();

		assertThat(tools().checkAffordability()).startsWith("Affordable:");

		assertThat(phases.phase(userId)).isEqualTo(Phase.DOCUMENTS);
	}

	@Test
	void aCheckThatCantTellYetDoesNot() {
		keyFacts();
		home();

		assertThat(tools().checkAffordability()).startsWith("Can't tell yet.");

		assertThat(phases.phase(userId)).isEqualTo(Phase.AFFORDABILITY);
	}

	@Test
	void theNoteSaysWhatIsMissingBeforeTheBanks() {
		assertThat(phases.current(userId).note()).startsWith("Where the conversation is now: getting to know you.")
				.contains("can't go to the banks yet").contains("netIncome");

		ready();

		assertThat(phases.current(userId).note()).startsWith("Where the conversation is now: ready.")
				.doesNotContain("can't go to the banks");
	}

	private AdvisorTools tools() {
		return new AdvisorTools(userId, profile, property, listings, banks, loans, documents, phases,
				UserStatements.from(List.of(), ""), events::add);
	}

	private void save(ProfileKey key, String value) {
		profile.save(userId, key, value, ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
	}

	private void keyFacts() {
		save(ProfileKey.NET_INCOME, "3200");
		save(ProfileKey.SAVINGS, "40000");
		save(ProfileKey.HAS_CO_APPLICANT, "no");
		save(ProfileKey.MONTHLY_DEBT_PAYMENTS, "0");
	}

	private void home() {
		property.updateFromChat(userId, Map.of(PropertyKey.TYPE, "apartment", PropertyKey.PRICE, "185000",
				PropertyKey.LOCATION, "Kristiine, Tallinn"), ProfileField.Status.CONFIRMED, false);
	}

	/** Every fact, the property and the four documents a bank needs, all confirmed. */
	private void ready() {
		keyFacts();
		save(ProfileKey.EMPLOYMENT, "employed");
		save(ProfileKey.CREDIT_CARD_LIMITS, "0");
		save(ProfileKey.DEPENDANTS, "0");
		save(ProfileKey.MONTHLY_LIVING_COSTS, "900");
		home();
		for (DocumentType type : List.of(DocumentType.ID, DocumentType.PAYSLIP, DocumentType.BANK_STATEMENT,
				DocumentType.DOWN_PAYMENT_PROOF)) {
			confirmDocument(type);
		}
	}

	/** Asked for, uploaded and read with nothing to confirm, so it's confirmed straight away. */
	private void confirmDocument(DocumentType type) {
		documents.request(userId, type);
		UUID id = documents.upload(userId, type, type.name().toLowerCase() + ".pdf").id();
		documents.recordReading(userId, id, Map.of());
	}
}
