package com.seb.mortgage.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;

import tools.jackson.databind.ObjectMapper;

/** Reading an uploaded document (#72), with a stand-in for the LLM. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class DocumentReaderTest {

	@Autowired DocumentService documents;
	@Autowired ProfileService profile;
	@Autowired ObjectMapper mapper;
	@Autowired DocumentReader withoutLlm;

	private final DocumentReaderAgent agent = mock(DocumentReaderAgent.class);

	@Test
	void aValueTheUserGaveIsReadExactlyAndNothingIsMadeUp() {
		UUID userId = UUID.randomUUID();
		profile.save(userId, ProfileKey.NET_INCOME, "2300", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();

		DocumentReader.Reading reading = reader().read(userId, payslip, List.of("I earn 2300 a month"));

		assertThat(reading.read()).isTrue();
		assertThat(reading.fields()).containsExactly(
				new ProfileFieldView("netIncome", "2300", ProfileField.Source.DOCUMENT, ProfileField.Status.UNCERTAIN,
						"PAYSLIP"));
		assertThat(reading.document().values()).containsExactly(Map.entry("netIncome", "2300"));
		assertThat(reading.document().status()).isEqualTo(Document.Status.UPLOADED);
		verify(agent, never()).read(any(), anyList(), anyString(), anyList());
	}

	@Test
	void aValueTheUserHasNotGivenIsMadeUpByTheAgent() {
		UUID userId = UUID.randomUUID();
		when(agent.read(eq(DocumentType.PAYSLIP), eq(List.of(ProfileKey.NET_INCOME)), anyString(),
				eq(List.of("I'm a nurse")))).thenReturn(Map.of("netIncome", "1 650"));
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();

		DocumentReader.Reading reading = reader().read(userId, payslip, List.of("I'm a nurse"));

		assertThat(reading.fields()).extracting(ProfileFieldView::key, ProfileFieldView::value)
				.containsExactly(tuple("netIncome", "1650"));
		assertThat(profile.fields(userId)).singleElement().satisfies(field -> {
			assertThat(field.source()).isEqualTo(ProfileField.Source.DOCUMENT);
			assertThat(field.status()).isEqualTo(ProfileField.Status.UNCERTAIN);
		});
	}

	@Test
	void aBankStatementMixesWhatTheUserSaidWithMadeUpValues() {
		UUID userId = UUID.randomUUID();
		profile.save(userId, ProfileKey.MONTHLY_DEBT_PAYMENTS, "0", ProfileField.Source.CHAT,
				ProfileField.Status.CONFIRMED);
		when(agent.read(eq(DocumentType.BANK_STATEMENT), eq(List.of(ProfileKey.MONTHLY_LIVING_COSTS)), anyString(),
				anyList())).thenReturn(Map.of("monthlyLivingCosts", "640", "monthlyDebtPayments", "300"));
		UUID statement = documents.upload(userId, DocumentType.BANK_STATEMENT, "statement.pdf").id();

		DocumentReader.Reading reading = reader().read(userId, statement, List.of());

		// The agent's contradicting debt figure is ignored: the user said 0.
		assertThat(reading.document().values())
				.containsExactly(Map.entry("monthlyDebtPayments", "0"), Map.entry("monthlyLivingCosts", "640"));
	}

	@Test
	void anImplausibleOrBrokenAnswerIsNotSaved() {
		UUID userId = UUID.randomUUID();
		when(agent.read(any(), anyList(), anyString(), anyList())).thenReturn(Map.of("netIncome", "5000000"));
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();

		DocumentReader.Reading reading = reader().read(userId, payslip, List.of());

		assertThat(reading.read()).isFalse();
		assertThat(profile.fields(userId)).isEmpty();
		assertThat(documents.find(userId, payslip).orElseThrow().values()).isEmpty();
	}

	@Test
	void aFailingAgentReadsNothing() {
		UUID userId = UUID.randomUUID();
		when(agent.read(any(), anyList(), anyString(), anyList())).thenThrow(new IllegalStateException("down"));
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();

		assertThat(reader().read(userId, payslip, List.of()).read()).isFalse();
		assertThat(profile.fields(userId)).isEmpty();
	}

	@Test
	void aDocumentWithNothingToReadIsConfirmedStraightAway() {
		UUID userId = UUID.randomUUID();
		UUID id = documents.upload(userId, DocumentType.ID, "passport.jpg").id();

		DocumentReader.Reading reading = reader().read(userId, id, List.of());

		assertThat(reading.read()).isTrue();
		assertThat(reading.fields()).isEmpty();
		assertThat(reading.document().status()).isEqualTo(Document.Status.CONFIRMED);
	}

	/** Without an API key there is no reader agent: nothing is read, not even what the user said. */
	@Test
	void withoutAnLlmNothingIsRead() {
		UUID userId = UUID.randomUUID();
		profile.save(userId, ProfileKey.NET_INCOME, "2300", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		UUID payslip = documents.upload(userId, DocumentType.PAYSLIP, "payslip.pdf").id();

		DocumentReader.Reading reading = withoutLlm.read(userId, payslip, List.of());

		assertThat(reading.read()).isFalse();
		assertThat(profile.fields(userId)).extracting(ProfileFieldView::source).containsExactly(ProfileField.Source.CHAT);
		assertThat(reading.document().status()).isEqualTo(Document.Status.UPLOADED);
	}

	private DocumentReader reader() {
		DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
		beans.registerSingleton("documentReaderAgent", agent);
		return new DocumentReader(beans.getBeanProvider(DocumentReaderAgent.class), documents, profile, mapper);
	}
}
