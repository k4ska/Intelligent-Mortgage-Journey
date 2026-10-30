package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Negotiating the margin through the API. No API key in tests, so the {@link Negotiator}
 * reads replies by keywords. SEB: base margin 1.65%, lowest 1.35%, 2 rounds.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NegotiationFlowTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ProfileService profile;
	@Autowired PropertyService properties;

	private record Thread(MockHttpSession session, String id) {}

	@Test
	void eachAskLowersTheMarginOneStepUntilTheFinalOffer() throws Exception {
		Thread seb = sebOffer();

		reply(seb, "Could you lower the margin a little?");
		JsonNode second = lastEmail(seb);
		assertThat(second.path("type").asString()).isEqualTo("offer");
		assertThat(second.path("offer").path("marginPct").asDouble()).isEqualTo(1.5);
		assertThat(second.path("body").asString()).contains("margin of 1.5%").doesNotContain("final offer");
		assertThat(thread(seb).path("status").asString()).isEqualTo("OFFER");

		reply(seb, "Thanks, can you do any better?");
		JsonNode third = lastEmail(seb);
		assertThat(third.path("offer").path("marginPct").asDouble()).isEqualTo(1.35);
		assertThat(third.path("body").asString()).contains("This is our final offer.");

		reply(seb, "Please lower it once more.");
		JsonNode fourth = lastEmail(seb);
		assertThat(fourth.path("type").asString()).isEqualTo("message");
		assertThat(fourth.path("offer").isNull()).isTrue();
		assertThat(fourth.path("body").asString()).contains("already the best we can do");
	}

	@Test
	void anotherBanksOfferGetsTheLowestMarginStraightAway() throws Exception {
		Thread seb = sebOffer();

		reply(seb, "LHV offered me a better deal, can you match it?");

		JsonNode counter = lastEmail(seb);
		assertThat(counter.path("offer").path("marginPct").asDouble()).isEqualTo(1.35);
		assertThat(counter.path("body").asString()).contains("This is our final offer.");
	}

	@Test
	void aLowerMarginLowersTheRateAndThePayment() throws Exception {
		Thread seb = sebOffer();
		JsonNode first = lastEmail(seb).path("offer");

		reply(seb, "Can you lower the margin?");

		JsonNode better = lastEmail(seb).path("offer");
		assertThat(better.path("amount").asDouble()).isEqualTo(first.path("amount").asDouble());
		assertThat(better.path("ratePct").asDouble()).isEqualTo(first.path("ratePct").asDouble() - 0.15,
				org.assertj.core.data.Offset.offset(0.001));
		assertThat(better.path("monthlyPayment").asDouble()).isLessThan(first.path("monthlyPayment").asDouble());
	}

	@Test
	void aReplyThatDoesntAskForMoreGetsTheUsualAnswer() throws Exception {
		Thread seb = sebOffer();

		reply(seb, "Thank you, what documents do you need from me?");

		JsonNode answer = lastEmail(seb);
		assertThat(answer.path("type").asString()).isEqualTo("confirmation");
		assertThat(thread(seb).path("emails").size()).isEqualTo(3);
	}

	@Test
	void onlyTheNewestOfferCanBeAccepted() throws Exception {
		Thread seb = sebOffer();
		String firstOffer = lastEmail(seb).path("id").asString();
		reply(seb, "Could you lower the margin?");
		String newerOffer = lastEmail(seb).path("id").asString();

		accept(seb, firstOffer).andExpect(status().isConflict());
		accept(seb, newerOffer).andExpect(status().isNoContent());
		assertThat(thread(seb).path("status").asString()).isEqualTo("ACCEPTED");
	}

	@Test
	void theKeywordReaderTellsAnAskFromAQuestion() {
		Negotiator negotiator = new Negotiator(null, new BankCatalogService());
		BankCatalogService.Bank seb = new BankCatalogService().find("seb");

		assertThat(negotiator.byKeywords(seb, "Could you lower the margin?").asksForBetterOffer()).isTrue();
		assertThat(negotiator.byKeywords(seb, "What documents do you need?").asksForBetterOffer()).isFalse();
		assertThat(negotiator.byKeywords(seb, "Swedbank offered less, can you match it?").mentionsCompetingOffer())
				.isTrue();
		assertThat(negotiator.byKeywords(seb, "SEB, can you lower the margin?").mentionsCompetingOffer()).isFalse();
	}

	/** A user with an offer from SEB: the first email in the thread. */
	private Thread sebOffer() throws Exception {
		String credentials = "{\"email\":\"negotiate-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		UUID userId = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		profile.save(userId, ProfileKey.NET_INCOME, "5000", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		profile.save(userId, ProfileKey.SAVINGS, "40000", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		properties.updateFromChat(userId, Map.of(PropertyKey.TYPE, "APARTMENT", PropertyKey.PRICE, "200000",
				PropertyKey.LOCATION, "Tallinn"), ProfileField.Status.CONFIRMED, true);
		String response = mvc.perform(post("/api/applications").session(session)
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\"]}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Thread seb = new Thread(session, mapper.readTree(response).path("threads").get(0).path("threadId").asString());
		assertThat(lastEmail(seb).path("offer").path("marginPct").asDouble()).isEqualTo(1.65);
		return seb;
	}

	private void reply(Thread thread, String body) throws Exception {
		mvc.perform(post("/api/inbox/" + thread.id() + "/reply").session(thread.session())
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("body", body))))
				.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.ResultActions accept(Thread thread, String emailId) throws Exception {
		return mvc.perform(post("/api/inbox/" + thread.id() + "/accept").session(thread.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailId\":\"" + emailId + "\"}"));
	}

	private JsonNode thread(Thread thread) throws Exception {
		return mapper.readTree(mvc.perform(get("/api/inbox/" + thread.id()).session(thread.session()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode lastEmail(Thread thread) throws Exception {
		JsonNode emails = thread(thread).path("emails");
		return emails.get(emails.size() - 1);
	}
}
