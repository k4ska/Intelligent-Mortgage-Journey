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
import com.seb.mortgage.documents.Document;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentType;
import com.seb.mortgage.documents.DocumentView;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;

/** The advisor's document tools (#72): asking for a document, and confirming what was read from one. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class DocumentToolsTest {

	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired ListingReader listings;
	@Autowired BankCatalogService banks;
	@Autowired LoanProfileAssembler loans;
	@Autowired DocumentService documents;

	private final List<StreamEvent> events = new ArrayList<>();

	private AdvisorTools tools(UUID userId, List<String> earlier, String userSaid) {
		return new AdvisorTools(userId, profile, property, listings, banks, loans, documents,
				UserStatements.from(earlier, userSaid), events::add);
	}

	@Test
	void requestingADocumentPutsItInThePanelWithWhereToGetIt() {
		UUID userId = UUID.randomUUID();

		String note = tools(userId, List.of(), "").requestDocument(List.of("payslip"));

		assertThat(note).isEqualTo("PAYSLIP (" + DocumentType.PAYSLIP.label() + ") is needed; where to get it: "
				+ DocumentType.PAYSLIP.whereToGet());
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.type()).isEqualTo("document_status");
			assertThat(event.data()).containsEntry("type", DocumentType.PAYSLIP)
					.containsEntry("status", Document.Status.NEEDED)
					.containsEntry("whereToGet", DocumentType.PAYSLIP.whereToGet());
		});
		assertThat(documents.checklist(userId)).filteredOn(d -> d.type() == DocumentType.PAYSLIP)
				.extracting(DocumentView::status).containsExactly(Document.Status.NEEDED);
	}

	@Test
	void severalDocumentsInOneCall() {
		UUID userId = UUID.randomUUID();

		tools(userId, List.of(), "").requestDocument(List.of("ID", "PAYSLIP", "BANK_STATEMENT"));

		assertThat(events).extracting(event -> event.data().get("type"))
				.containsExactly(DocumentType.ID, DocumentType.PAYSLIP, DocumentType.BANK_STATEMENT);
	}

	@Test
	void anUploadedDocumentIsNotAskedForAgain() {
		UUID userId = UUID.randomUUID();
		documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf");

		assertThat(tools(userId, List.of(), "").requestDocument(List.of("PAYSLIP"))).contains("already uploaded");
		assertThat(events).isEmpty();
	}

	@Test
	void coApplicantDocumentsOnlyWithACoApplicantAndUnknownTypesAreRefused() {
		UUID userId = UUID.randomUUID();
		AdvisorTools tools = tools(userId, List.of(), "");

		assertThat(tools.requestDocument(List.of("CO_APPLICANT_PAYSLIP", "diploma")))
				.contains("CO_APPLICANT_PAYSLIP is only needed when").contains("diploma is not a document type");
		assertThat(events).isEmpty();
	}

	@Test
	void yesConfirmsTheValuesAndTheDocument() {
		UUID userId = UUID.randomUUID();
		readPayslip(userId, "2350");

		String note = tools(userId, List.of(), "Yes, that's right!").confirmDocument("PAYSLIP", "yes, that's right");

		assertThat(note).isEqualTo("PAYSLIP is confirmed, and so are netIncome=2350.");
		assertThat(profile.fields(userId)).singleElement().satisfies(field -> {
			assertThat(field.status()).isEqualTo(ProfileField.Status.CONFIRMED);
			assertThat(field.source()).isEqualTo(ProfileField.Source.DOCUMENT);
		});
		assertThat(status(userId)).isEqualTo(Document.Status.CONFIRMED);
		assertThat(events).extracting(StreamEvent::type).containsExactly("profile_updated", "document_status");
	}

	/** The advisor can't confirm on the strength of an old "yes", or of no answer at all. */
	@Test
	void onlyTheUsersLatestMessageCanConfirm() {
		UUID userId = UUID.randomUUID();
		readPayslip(userId, "2350");

		assertThat(tools(userId, List.of("yes"), "").confirmDocument("PAYSLIP", "yes")).startsWith("Not confirmed");
		assertThat(tools(userId, List.of(), "What now?").confirmDocument("PAYSLIP", "yes")).startsWith("Not confirmed");
		assertThat(status(userId)).isEqualTo(Document.Status.UPLOADED);
		assertThat(profile.fields(userId)).extracting(ProfileFieldView::status)
				.containsExactly(ProfileField.Status.UNCERTAIN);
	}

	@Test
	void nothingToConfirmBeforeTheDocumentIsRead() {
		UUID userId = UUID.randomUUID();
		documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf");

		assertThat(tools(userId, List.of(), "yes").confirmDocument("PAYSLIP", "yes"))
				.startsWith("Not confirmed: nothing has been read from a PAYSLIP yet");
	}

	/** "No, it's 2,400": the user's value is saved from the chat, and the document is settled. */
	@Test
	void aCorrectionSavesTheUsersValueAndConfirmsTheDocument() {
		UUID userId = UUID.randomUUID();
		readPayslip(userId, "2350");

		String note = tools(userId, List.of(), "No, it's 2,400").updateProfile(
				List.of(new AdvisorTools.FieldUpdate("netIncome", "2400", "it's 2,400")));

		assertThat(note).contains("Saved: netIncome=2400").contains("PAYSLIP is now confirmed");
		assertThat(profile.fields(userId)).containsExactly(new ProfileFieldView("netIncome", "2400",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED, null));
		assertThat(status(userId)).isEqualTo(Document.Status.CONFIRMED);
		assertThat(events).extracting(StreamEvent::type).containsExactly("profile_updated", "document_status");
	}

	/** What the document reader would have done: an uncertain value from the payslip. */
	private void readPayslip(UUID userId, String netIncome) {
		UUID id = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();
		profile.saveFromDocument(userId, ProfileKey.NET_INCOME, netIncome, "PAYSLIP");
		documents.recordReading(userId, id, Map.of("netIncome", netIncome));
	}

	private Document.Status status(UUID userId) {
		return documents.checklist(userId).stream().filter(d -> d.type() == DocumentType.PAYSLIP).findFirst()
				.orElseThrow().status();
	}
}
