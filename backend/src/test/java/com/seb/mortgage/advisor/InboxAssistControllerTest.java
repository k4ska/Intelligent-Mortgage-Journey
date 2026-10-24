package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The advisor next to a thread and its triggers (#80), over HTTP as a logged-in user, without an LLM. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
// Printing a result reads its headers while the stream may still be writing them, which
// fails now and then under load ("End size 1 is less than fixed size 2").
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class InboxAssistControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;

	@Test
	void askingNextToAThreadIsTaggedWithItInTheChatHistory() throws Exception {
		MockHttpSession user = login();
		JsonNode application = apply(user);
		String threadId = application.path("threads").path(0).path("threadId").asString();

		String body = stream(assist(user, threadId, "{\"text\":\"Explain this offer\"}"));

		assertThat(body).contains("event:text").contains("event:done");
		mvc.perform(get("/api/chat").session(user))
				.andExpect(jsonPath("$[-2].role").value("user"))
				.andExpect(jsonPath("$[-2].text").value("Explain this offer"))
				.andExpect(jsonPath("$[-2].threadId").value(threadId))
				.andExpect(jsonPath("$[-1].role").value("advisor"))
				.andExpect(jsonPath("$[-1].threadId").value(threadId))
				.andExpect(jsonPath("$[0].threadId").isEmpty());
	}

	@Test
	void onlyTheUsersOwnThreadAndAQuestion() throws Exception {
		MockHttpSession user = login();
		String threadId = apply(user).path("threads").path(0).path("threadId").asString();

		assist(login(), threadId, "{\"text\":\"Explain this offer\"}").andExpect(status().isNotFound());
		assist(user, threadId, "{\"text\":\"  \"}").andExpect(status().isBadRequest());
	}

	@Test
	void theTriggersCheckWhatTheyAreAbout() throws Exception {
		MockHttpSession user = login();
		JsonNode application = apply(user);
		String applicationId = application.path("applicationId").asString();
		String threadId = application.path("threads").path(0).path("threadId").asString();

		String body = stream(trigger(user, "{\"trigger\":\"APPLICATION_SENT\",\"applicationId\":\"" + applicationId + "\"}"));
		assertThat(body).contains("event:done");
		mvc.perform(get("/api/chat").session(user))
				.andExpect(jsonPath("$[-2].role").value("system"))
				.andExpect(jsonPath("$[-2].text").value("Sent the application to SEB"));

		trigger(login(), "{\"trigger\":\"APPLICATION_SENT\",\"applicationId\":\"" + applicationId + "\"}")
				.andExpect(status().isNotFound());
		trigger(user, "{\"trigger\":\"APPLICATION_SENT\"}").andExpect(status().isBadRequest());
		// The offer isn't accepted yet (that is #78's endpoint).
		trigger(user, "{\"trigger\":\"OFFER_ACCEPTED\",\"threadId\":\"" + threadId + "\"}")
				.andExpect(status().isConflict());
		trigger(user, "{\"trigger\":\"OFFER_ACCEPTED\",\"threadId\":\"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isNotFound());
		trigger(user, "{\"trigger\":\"SOMETHING_ELSE\"}").andExpect(status().isBadRequest());
	}

	private ResultActions assist(MockHttpSession session, String threadId, String json) throws Exception {
		return mvc.perform(post("/api/inbox/" + threadId + "/assist").session(session)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions trigger(MockHttpSession session, String json) throws Exception {
		return mvc.perform(post("/api/chat/messages").session(session)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String stream(ResultActions started) throws Exception {
		return mvc.perform(asyncDispatch(started.andExpect(status().isOk()).andReturn()))
				.andReturn().getResponse().getContentAsString();
	}

	private JsonNode apply(MockHttpSession session) throws Exception {
		String json = mvc.perform(post("/api/applications").session(session)
						.contentType(MediaType.APPLICATION_JSON).content("{\"bankIds\":[\"seb\"]}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return mapper.readTree(json);
	}

	private MockHttpSession login() throws Exception {
		String credentials = "{\"email\":\"assist-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(credentials))
				.andExpect(status().isOk()).andReturn().getRequest().getSession(false);
	}
}
