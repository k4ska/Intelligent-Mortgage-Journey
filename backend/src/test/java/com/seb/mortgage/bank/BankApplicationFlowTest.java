package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
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

import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Sending an application and reading the inbox through the API, as a logged-in user. No LLM involved. */
@SpringBootTest
@AutoConfigureMockMvc
class BankApplicationFlowTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ProfileService profile;
	@Autowired PropertyService properties;
	@Autowired PropertyService property;
	@Autowired ApplicationRepository applications;

	private record LoggedIn(UUID userId, MockHttpSession session) {}

	@Test
	void listsTheFiveBanks() throws Exception {
		mvc.perform(get("/api/banks").session(login().session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains("seb", "swedbank", "lhv", "luminor", "coop")));
	}

	@Test
	void applyingToSebPutsAnUnreadSebEmailInTheInboxAndOpeningItMarksItRead() throws Exception {
		LoggedIn user = login();
		JsonNode result = apply(user, "[\"seb\"]");
		String threadId = result.path("threads").get(0).path("threadId").asString();

		mvc.perform(get("/api/inbox").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].bank.id").value("seb"))
				.andExpect(jsonPath("$[0].unread").value(true))
				.andExpect(jsonPath("$[0].lastEmail.subject").value("Your mortgage application is received"));

		mvc.perform(get("/api/inbox/" + threadId).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emails", hasSize(1)))
				.andExpect(jsonPath("$.emails[0].from").value("BANK"))
				.andExpect(jsonPath("$.emails[0].type").value("question"));

		mvc.perform(get("/api/inbox").session(user.session()))
				.andExpect(jsonPath("$[0].unread").value(false));
	}

	@Test
	void twoBanksGiveTwoThreadsWithDifferentTones() throws Exception {
		LoggedIn user = login();
		JsonNode result = apply(user, "[\"lhv\", \"luminor\"]");
		assertThat(result.path("threads")).hasSize(2);

		String lhv = body(user, result.path("threads").get(0).path("threadId").asString());
		String luminor = body(user, result.path("threads").get(1).path("threadId").asString());
		assertThat(lhv).startsWith("Application received.");
		assertThat(luminor).startsWith("Dear applicant");
	}

	@Test
	void theBankOnlyAsksForIncomeWhenTheProfileHasNone() throws Exception {
		LoggedIn withoutIncome = login();
		LoggedIn withIncome = login();
		profile.save(withIncome.userId(), ProfileKey.NET_INCOME, "2350",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);

		String asked = body(withoutIncome, apply(withoutIncome, "[\"seb\"]").path("threads").get(0).path("threadId").asString());
		String notAsked = body(withIncome, apply(withIncome, "[\"seb\"]").path("threads").get(0).path("threadId").asString());
		assertThat(asked).contains("monthly net income");
		assertThat(notAsked).doesNotContain("net income");
	}

	@Test
	void aBankEmailStoresTheCalculatedOfferAndUpdatesTheThreadStatus() throws Exception {
		LoggedIn user = login();
		profile.save(user.userId(), ProfileKey.NET_INCOME, "5000",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		profile.save(user.userId(), ProfileKey.SAVINGS, "40000",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		properties.updateFromChat(user.userId(), java.util.Map.of(
				PropertyKey.TYPE, "APARTMENT",
				PropertyKey.PRICE, "200000",
				PropertyKey.LOCATION, "Tallinn"), ProfileField.Status.CONFIRMED, true);

		JsonNode result = apply(user, "[\"seb\"]");
		String threadId = result.path("threads").get(0).path("threadId").asString();

		mvc.perform(get("/api/inbox/" + threadId).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OFFER"))
				.andExpect(jsonPath("$.emails[0].type").value("offer"))
				.andExpect(jsonPath("$.emails[0].offer.amount").value(160000.0))
				.andExpect(jsonPath("$.emails[0].offer.termYears").value(25));
	}

	@Test
	void theSnapshotIsFrozenWhenTheApplicationIsSent() throws Exception {
		LoggedIn user = login();
		profile.save(user.userId(), ProfileKey.NET_INCOME, "2350",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		UUID applicationId = UUID.fromString(apply(user, "[\"seb\"]").path("applicationId").asString());

		profile.save(user.userId(), ProfileKey.NET_INCOME, "9999",
				ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);

		Application application = applications.findById(applicationId).orElseThrow();
		assertThat(application.getSnapshotJson()).contains("2350").doesNotContain("9999");
		assertThat(application.getBankIdsJson()).isEqualTo("[\"seb\"]");
	}

	@Test
	void thePropertyIsFrozenWhenTheApplicationIsSent() throws Exception {
		LoggedIn user = login();
		property.updateFromChat(user.userId(), Map.of(PropertyKey.PRICE, "185000"),
				ProfileField.Status.CONFIRMED, false);
		UUID applicationId = UUID.fromString(apply(user, "[\"seb\"]").path("applicationId").asString());

		property.updateFromChat(user.userId(), Map.of(PropertyKey.PRICE, "310000"),
				ProfileField.Status.CONFIRMED, false);

		Application application = applications.findById(applicationId).orElseThrow();
		assertThat(application.getSnapshotJson()).contains("185000").doesNotContain("310000");
	}

	@Test
	void anUnknownBankOrNoBankIsABadRequest() throws Exception {
		LoggedIn user = login();
		mvc.perform(post("/api/applications").session(user.session())
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\",\"nordea\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(containsString("nordea")));
		mvc.perform(post("/api/applications").session(user.session())
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[]}"))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/api/inbox").session(user.session())).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void aUserCannotOpenSomeoneElsesThread() throws Exception {
		LoggedIn owner = login();
		String threadId = apply(owner, "[\"seb\"]").path("threads").get(0).path("threadId").asString();

		mvc.perform(get("/api/inbox/" + threadId).session(login().session()))
				.andExpect(status().isNotFound());
	}

	@Test
	void theInboxNeedsALogin() throws Exception {
		mvc.perform(get("/api/inbox")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\"]}"))
				.andExpect(status().isUnauthorized());
	}

	private LoggedIn login() throws Exception {
		String credentials = "{\"email\":\"bank-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		UUID userId = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
		return new LoggedIn(userId, (MockHttpSession) login.getRequest().getSession(false));
	}

	private JsonNode apply(LoggedIn user, String bankIds) throws Exception {
		String response = mvc.perform(post("/api/applications").session(user.session())
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":" + bankIds + "}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return mapper.readTree(response);
	}

	private String body(LoggedIn user, String threadId) throws Exception {
		String response = mvc.perform(get("/api/inbox/" + threadId).session(user.session()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return mapper.readTree(response).path("emails").get(0).path("body").asString();
	}
}
