package com.seb.mortgage.advisor;

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

/** The chart buttons under the chat (#160, point 3): charts shown without the model. */
@SpringBootTest
@AutoConfigureMockMvc
class ChartButtonsTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired ProfileService profile;
	@Autowired PropertyService properties;

	private record User(UUID id, MockHttpSession session) {}

	@Test
	void noChartsUntilTheLoanIsKnown() throws Exception {
		User user = register();

		assertThat(json(mvc.perform(get("/api/chat/charts").session(user.session())))).isEmpty();
		mvc.perform(post("/api/chat/charts").session(user.session()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"chartType\":\"rate_payment\"}")).andExpect(status().isConflict());
	}

	@Test
	void aButtonShowsTheChartAsAnAdvisorMessageThatStays() throws Exception {
		User user = register();
		loanKnown(user);

		JsonNode available = json(mvc.perform(get("/api/chat/charts").session(user.session())));
		assertThat(available.toString()).isEqualTo("[\"rate_payment\",\"cost_breakdown\"]");

		JsonNode shown = json(mvc.perform(post("/api/chat/charts").session(user.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"chartType\":\"cost_breakdown\"}")));
		assertThat(shown.path("role").asString()).isEqualTo("advisor");
		JsonNode data = shown.path("chart").path("data");
		assertThat(shown.path("chart").path("chartType").asString()).isEqualTo("cost_breakdown");
		assertThat(data.path("price").asDouble()).isEqualTo(200000);
		assertThat(data.path("loanAmount").asDouble()).isEqualTo(160000);
		assertThat(data.path("totalInterest").asDouble())
				.isEqualTo(Math.round((data.path("monthlyPayment").asDouble() * data.path("termYears").asInt() * 12
						- 160000) * 100) / 100.0);

		JsonNode history = json(mvc.perform(get("/api/chat").session(user.session())));
		JsonNode last = history.get(history.size() - 1);
		assertThat(last.path("id").asLong()).isEqualTo(shown.path("id").asLong());
		assertThat(last.path("chart")).isEqualTo(shown.path("chart"));
	}

	@Test
	void afterAcceptingAnOfferTheChartsShowThatOffer() throws Exception {
		User user = register();
		loanKnown(user);
		String thread = json(mvc.perform(post("/api/applications").session(user.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\"]}")))
				.path("threads").get(0).path("threadId").asString();
		mvc.perform(post("/api/inbox/" + thread + "/reply").session(user.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Could you lower the margin a little?\"}"))
				.andExpect(status().isNoContent());
		JsonNode emails = json(mvc.perform(get("/api/inbox/" + thread).session(user.session()))).path("emails");
		JsonNode offerEmail = emails.get(emails.size() - 1);
		JsonNode offer = offerEmail.path("offer");
		assertThat(offer.path("marginPct").asDouble()).as("negotiated").isEqualTo(1.5);
		mvc.perform(post("/api/inbox/" + thread + "/accept").session(user.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailId\":\"" + offerEmail.path("id").asString() + "\"}"))
				.andExpect(status().isNoContent());

		JsonNode rate = show(user, "rate_payment").path("chart");
		assertThat(rate.path("title").asString()).contains("SEB's offer");
		assertThat(rate.path("data").path("marginPct").asDouble()).isEqualTo(1.5);
		assertThat(rate.path("data").path("loanAmount").asDouble()).isEqualTo(offer.path("amount").asDouble());
		JsonNode today = null;
		for (JsonNode point : rate.path("data").path("points")) {
			if (point.path("today").asBoolean()) {
				today = point;
			}
		}
		assertThat(today).isNotNull();
		assertThat(today.path("monthlyPayment").asDouble()).isEqualTo(offer.path("monthlyPayment").asDouble());

		JsonNode cost = show(user, "cost_breakdown").path("chart");
		assertThat(cost.path("title").asString()).contains("SEB's offer");
		assertThat(cost.path("data").path("monthlyPayment").asDouble()).isEqualTo(offer.path("monthlyPayment").asDouble());
		assertThat(cost.path("data").path("downPayment").asDouble()).isEqualTo(200000 - offer.path("amount").asDouble());
	}

	@Test
	void anUnknownChartIsRefused() throws Exception {
		User user = register();
		loanKnown(user);

		mvc.perform(post("/api/chat/charts").session(user.session()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"chartType\":\"pie\"}")).andExpect(status().isBadRequest());
	}

	private User register() throws Exception {
		String credentials = "{\"email\":\"charts-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
		return new User(id, (MockHttpSession) login.getRequest().getSession(false));
	}

	/** Everything the affordability check needs: a single buyer earning 5000 with 40 000 saved, and a 200 000 apartment. */
	private void loanKnown(User user) {
		Map.of(ProfileKey.NET_INCOME, "5000", ProfileKey.SAVINGS, "40000", ProfileKey.MONTHLY_DEBT_PAYMENTS, "0",
				ProfileKey.CREDIT_CARD_LIMITS, "0", ProfileKey.HAS_CO_APPLICANT, "no", ProfileKey.DEPENDANTS, "0",
				ProfileKey.MONTHLY_LIVING_COSTS, "600")
				.forEach((key, value) -> profile.save(user.id(), key, value, ProfileField.Source.CHAT,
						ProfileField.Status.CONFIRMED));
		properties.updateFromChat(user.id(), Map.of(PropertyKey.TYPE, "APARTMENT", PropertyKey.PRICE, "200000",
				PropertyKey.LOCATION, "Tallinn"), ProfileField.Status.CONFIRMED, true);
	}

	private JsonNode show(User user, String chartType) throws Exception {
		return json(mvc.perform(post("/api/chat/charts").session(user.session())
				.contentType(MediaType.APPLICATION_JSON).content("{\"chartType\":\"" + chartType + "\"}")));
	}

	private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
		return mapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}
}
