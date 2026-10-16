package com.seb.mortgage.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
		var session = login.getRequest().getSession(false);
		mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isOk());
		mvc.perform(get("/api/profile").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isUnauthorized());
	}
}