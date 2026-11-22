package com.seb.mortgage.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/** Chat history (#160): persistence, switching, authorization and reset. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@AutoConfigureMockMvc
class ChatHistoryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ConversationService conversations;

    private record LoggedIn(UUID userId, MockHttpSession session) {}

    @Test
    void savesOlderChatsAndSelectsTheirMessages() throws Exception {
        LoggedIn user = login();
        String originalId = mapper.readTree(mvc.perform(get("/api/chat/conversations").session(user.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString()).get(0).path("id").asString();
        conversations.append(user.userId(), Message.Role.USER, "First mortgage");

        String newId = mapper.readTree(mvc.perform(post("/api/chat/conversations").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString()).path("id").asString();
        assertThat(newId).isNotEqualTo(originalId);
        mvc.perform(get("/api/chat").session(user.session()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").value(ConversationService.GREETING));
        conversations.append(user.userId(), Message.Role.USER, "Second mortgage");
        mvc.perform(get("/api/chat/conversations").session(user.session()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Second mortgage"));

        mvc.perform(post("/api/chat/conversations/{id}/select", originalId).session(user.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat").session(user.session()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].text").value("First mortgage"));
        conversations.append(user.userId(), Message.Role.ADVISOR, "Saved reply");
        mvc.perform(post("/api/chat/conversations/{id}/select", newId).session(user.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat").session(user.session()))
                .andExpect(jsonPath("$[1].text").value("Second mortgage"));
        mvc.perform(post("/api/chat/conversations/{id}/select", originalId).session(user.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat").session(user.session()))
                .andExpect(jsonPath("$[2].text").value("Saved reply"));
    }

    @Test
    void deniesOtherUsersChatsAndResetRemovesAllSavedHistory() throws Exception {
        LoggedIn first = login();
        LoggedIn second = login();
        String originalId = mapper.readTree(mvc.perform(get("/api/chat/conversations").session(first.session()))
                .andReturn().getResponse().getContentAsString()).get(0).path("id").asString();
        mvc.perform(post("/api/chat/conversations").session(first.session())).andExpect(status().isOk());

        mvc.perform(post("/api/chat/conversations/{id}/select", originalId).session(second.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/conversations").session(second.session()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/chat/reset").session(first.session())).andExpect(status().isOk());
        mvc.perform(get("/api/chat/conversations").session(first.session()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/chat/conversations/{id}/select", originalId).session(first.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/conversations/{id}/select", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private LoggedIn login() throws Exception {
        String credentials = "{\"email\":\"history-" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        UUID id = UUID.fromString(mapper.readTree(login.getResponse().getContentAsString()).path("id").asString());
        return new LoggedIn(id, (MockHttpSession) login.getRequest().getSession(false));
    }
}