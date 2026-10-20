package com.seb.mortgage.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A corrected reply and its marker both survive a new history read. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class MessageCorrectionTest {

    @Autowired ConversationService conversations;
    @Autowired MessageRepository messages;

    @Test
    void persistsCorrectedTextAndFlag() {
        UUID userId = UUID.randomUUID();
        Message reply = conversations.append(userId, Message.Role.ADVISOR, "Original reply");

        assertThat(reply.isCorrected()).isFalse();
        conversations.correct(reply.getId(), "Safe reply");

        Message saved = messages.findById(reply.getId()).orElseThrow();
        assertThat(saved.getText()).isEqualTo("Safe reply");
        assertThat(saved.isCorrected()).isTrue();
        assertThat(conversations.history(userId).getLast().isCorrected()).isTrue();
    }
}
