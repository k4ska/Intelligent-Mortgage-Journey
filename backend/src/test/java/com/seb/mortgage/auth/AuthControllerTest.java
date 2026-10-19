package com.seb.mortgage.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	@Autowired MockMvc mvc;

	@Test
	void registerLoginMeLogoutAndProtectedEndpoint() throws Exception {
		String credentials = "{\"email\":\"auth-test@example.com\",\"password\":\"password123\"}";
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk());
		mvc.perform(get("/api/profile").session(session))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loggingInGivesANewSessionId() throws Exception {
		String credentials = "{\"email\":\"fixation-test@example.com\",\"password\":\"password123\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		MockHttpSession planted = new MockHttpSession();
		String plantedId = planted.getId();

		var login = mvc.perform(post("/api/auth/login").session(planted)
						.contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();

		assertThat(login.getRequest().getSession(false).getId()).isNotEqualTo(plantedId);
	}

	@Test
	void anyNonEmptyPasswordIsEnough() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"short-password@example.com\",\"password\":\"a\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void anEmptyPasswordOrABadEmailIsABadRequest() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"empty-password@example.com\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\",\"password\":\"a\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aWrongPasswordIsUnauthorized() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"wrong-password@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"wrong-password@example.com\",\"password\":\"nope-nope\"}"))
				.andExpect(status().isUnauthorized());
	}
}
