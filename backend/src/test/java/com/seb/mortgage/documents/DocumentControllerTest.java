package com.seb.mortgage.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;

import tools.jackson.databind.ObjectMapper;

/** The documents panel's endpoints (#72), as a logged-in user. */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ProfileService profile;
	@Autowired DocumentService documents;

	private record LoggedIn(UUID userId, MockHttpSession session) {}

	@Test
	void theChecklistListsWhatABankNeedsAndWhereToGetIt() throws Exception {
		mvc.perform(get("/api/documents").session(login().session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(6)))
				.andExpect(jsonPath("$[*].type", not(hasItem("CO_APPLICANT_PAYSLIP"))))
				.andExpect(jsonPath("$[*].status", everyItem(is("NOT_NEEDED_YET"))))
				.andExpect(jsonPath("$[1].type").value("PAYSLIP"))
				.andExpect(jsonPath("$[1].whereToGet")
						.value("Your employer, or the Tax and Customs Board (EMTA) e-services."));
	}

	@Test
	void coApplicantDocumentsOnlyWithACoApplicant() throws Exception {
		LoggedIn user = login();
		profile.save(user.userId(), ProfileKey.HAS_CO_APPLICANT, "yes", ProfileField.Source.CHAT,
				ProfileField.Status.CONFIRMED);

		mvc.perform(get("/api/documents").session(user.session()))
				.andExpect(jsonPath("$", hasSize(8)))
				.andExpect(jsonPath("$[*].type", hasItem("CO_APPLICANT_PAYSLIP")));
	}

	@Test
	void anyFileUploadsAndOnlyItsNameIsKept() throws Exception {
		LoggedIn user = login();
		documents.request(user.userId(), DocumentType.PAYSLIP);

		mvc.perform(multipart("/api/documents/PAYSLIP").file(file("C:\\fakepath\\payslip march.pdf"))
						.session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.type").value("PAYSLIP"))
				.andExpect(jsonPath("$.status").value("UPLOADED"))
				.andExpect(jsonPath("$.fileName").value("payslip march.pdf"))
				.andExpect(jsonPath("$.values").isEmpty());

		mvc.perform(get("/api/documents").session(user.session()))
				.andExpect(jsonPath("$[1].status").value("UPLOADED"))
				.andExpect(jsonPath("$[0].status").value("NOT_NEEDED_YET"));
	}

	@Test
	void anUnknownTypeOrACoApplicantDocumentWithoutOneIsABadRequest() throws Exception {
		LoggedIn user = login();
		mvc.perform(multipart("/api/documents/DIPLOMA").file(file("a.pdf")).session(user.session()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Unknown document type: DIPLOMA"));
		mvc.perform(multipart("/api/documents/CO_APPLICANT_PAYSLIP").file(file("a.pdf")).session(user.session()))
				.andExpect(status().isBadRequest());
	}

	/** #73: the advisor asks for a document before it can be uploaded. */
	@Test
	void aDocumentNobodyAskedForCantBeUploaded() throws Exception {
		LoggedIn user = login();

		mvc.perform(multipart("/api/documents/PAYSLIP").file(file("a.pdf")).session(user.session()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Your advisor hasn't asked for this document yet"));
		mvc.perform(get("/api/documents").session(user.session()))
				.andExpect(jsonPath("$[1].status").value("NOT_NEEDED_YET"));
	}

	/** The upload trigger through the chat endpoint. This test runs without an API key, so nothing is read. */
	@Test
	void theUploadTriggerStreamsTheTurnAndRejectsBadRequests() throws Exception {
		LoggedIn user = login();
		documents.request(user.userId(), DocumentType.PAYSLIP);
		String id = mapper.readTree(mvc.perform(multipart("/api/documents/PAYSLIP").file(file("payslip.pdf"))
				.session(user.session())).andReturn().getResponse().getContentAsString()).path("id").asString();

		var stream = mvc.perform(post("/api/chat/messages").session(user.session())
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
						.content("{\"trigger\":\"DOCUMENT_UPLOADED\",\"documentId\":\"" + id + "\"}"))
				.andExpect(status().isOk()).andReturn();
		String body = mvc.perform(asyncDispatch(stream))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("event:document_status").contains("event:done");

		mvc.perform(post("/api/chat/messages").session(user.session()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"trigger\":\"DOCUMENT_UPLOADED\",\"documentId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/chat/messages").session(user.session()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"trigger\":\"DOCUMENT_UPLOADED\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/chat/messages").session(user.session()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"trigger\":\"SOMETHING_ELSE\",\"documentId\":\"" + id + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void documentsNeedALogin() throws Exception {
		mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
		mvc.perform(multipart("/api/documents/PAYSLIP").file(file("a.pdf"))).andExpect(status().isUnauthorized());
	}

	private static MockMultipartFile file(String name) {
		return new MockMultipartFile("file", name, "application/pdf", new byte[] { 1, 2, 3 });
	}

	private LoggedIn login() throws Exception {
		String credentials = "{\"email\":\"docs-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		UUID userId = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
		return new LoggedIn(userId, (MockHttpSession) login.getRequest().getSession(false));
	}
}
