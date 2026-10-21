package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.seb.mortgage.bank.ApplicationRepository;
import com.seb.mortgage.bank.EmailRepository;
import com.seb.mortgage.bank.EmailThreadRepository;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentType;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;

import tools.jackson.databind.ObjectMapper;

/** Start over (#70) through the API, as a logged-in user. No LLM involved. */
@SpringBootTest
@AutoConfigureMockMvc
class StartOverTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ConversationService conversations;
	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired ApplicationRepository applications;
	@Autowired EmailThreadRepository threads;
	@Autowired EmailRepository emails;
	@Autowired DocumentService documents;

	private record LoggedIn(UUID userId, MockHttpSession session) {}

	@Test
	void wipesEverythingButTheAccount() throws Exception {
		LoggedIn user = login();
		fillIn(user);

		mvc.perform(post("/api/chat/reset").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].role").value("advisor"))
				.andExpect(jsonPath("$[0].text").value(ConversationService.GREETING));

		mvc.perform(get("/api/chat").session(user.session()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].text").value(ConversationService.GREETING));
		mvc.perform(get("/api/profile").session(user.session()))
				.andExpect(jsonPath("$.fields", hasSize(0)))
				.andExpect(jsonPath("$.property").doesNotExist());
		mvc.perform(get("/api/inbox").session(user.session()))
				.andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/documents").session(user.session()))
				.andExpect(jsonPath("$[1].status").value("NOT_NEEDED_YET"));
		assertThat(applications.findAll()).noneMatch(a -> a.getUserId().equals(user.userId()));
		assertThat(threads.findByUserIdOrderByCreatedAtDesc(user.userId())).isEmpty();

		// The account stays: the same session still works, and the user can carry on.
		conversations.append(user.userId(), Message.Role.USER, "Hi again");
		assertThat(conversations.history(user.userId())).extracting(Message::getText)
				.containsExactly(ConversationService.GREETING, "Hi again");
	}

	@Test
	void leavesOtherUsersAlone() throws Exception {
		LoggedIn user = login();
		LoggedIn other = login();
		fillIn(user);
		fillIn(other);
		long otherEmails = emailsOf(other);

		mvc.perform(post("/api/chat/reset").session(user.session())).andExpect(status().isOk());

		assertThat(conversations.history(other.userId())).hasSize(3);
		assertThat(profile.fields(other.userId())).hasSize(1);
		assertThat(property.active(other.userId())).isPresent();
		assertThat(threads.findByUserIdOrderByCreatedAtDesc(other.userId())).hasSize(1);
		assertThat(emailsOf(other)).isEqualTo(otherEmails).isPositive();
	}

	@Test
	void startingOverWithNothingYetIsFine() throws Exception {
		mvc.perform(post("/api/chat/reset").session(login().session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void needsALogin() throws Exception {
		mvc.perform(post("/api/chat/reset")).andExpect(status().isUnauthorized());
	}

	/** A conversation, a profile field, a property and an application to SEB with its email. */
	private void fillIn(LoggedIn user) throws Exception {
		conversations.append(user.userId(), Message.Role.USER, "I earn 2350 a month");
		conversations.append(user.userId(), Message.Role.ADVISOR, "Thanks, saved.");
		profile.save(user.userId(), ProfileKey.NET_INCOME, "2350", ProfileField.Source.CHAT,
				ProfileField.Status.CONFIRMED);
		property.updateFromChat(user.userId(), Map.of(PropertyKey.PRICE, "185000"), ProfileField.Status.CONFIRMED,
				true);
		mvc.perform(post("/api/applications").session(user.session())
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\"]}"))
				.andExpect(status().isOk());
		assertThat(emailsOf(user)).isPositive();
		documents.request(user.userId(), DocumentType.PAYSLIP);
		documents.upload(user.userId(), DocumentType.PAYSLIP, "payslip.pdf");
	}

	private long emailsOf(LoggedIn user) {
		return threads.findByUserIdOrderByCreatedAtDesc(user.userId()).stream()
				.mapToLong(thread -> emails.findByThreadIdOrderByCreatedAtAsc(thread.getId()).size()).sum();
	}

	private LoggedIn login() throws Exception {
		String credentials = "{\"email\":\"reset-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		UUID userId = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
		return new LoggedIn(userId, (MockHttpSession) login.getRequest().getSession(false));
	}
}
