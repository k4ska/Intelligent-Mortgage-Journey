package com.seb.mortgage.advisor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;

import reactor.core.publisher.Flux;

/** One advisor turn, end to end through the orchestrator, without a real LLM. */
class AdvisorOrchestratorTest {

	@Nested
	@SpringBootTest(properties = "spring.ai.openai.api-key=")
	@SuppressWarnings("unused")
	class WithoutKey {

		@Autowired AdvisorOrchestrator orchestrator;
		@Autowired ConversationService conversations;

		@Test
		void givesTheCannedReplyAndSavesBothMessages() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "Could I get a loan?").collectList().block();

			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(events.get(0)).isEqualTo(StreamEvent.text(AdvisorOrchestrator.OFFLINE_REPLY));

			List<Message> history = conversations.history(userId);
			assertThat(history).extracting(Message::getRole)
					.containsExactly(Message.Role.ADVISOR, Message.Role.USER, Message.Role.ADVISOR);
			assertThat(history).extracting(Message::getText).containsExactly(
					ConversationService.GREETING, "Could I get a loan?", AdvisorOrchestrator.OFFLINE_REPLY);
			assertThat(events.get(1)).isEqualTo(StreamEvent.done(history.get(2).getId()));
		}
	}

	@Nested
	@SpringBootTest(properties = "spring.ai.openai.api-key=sk-dummy")
	@Import(WithModel.FakeModelConfig.class)
	@SuppressWarnings("unused")
	class WithModel {

		@TestConfiguration
		static class FakeModelConfig {

			@Bean
			@Primary
			FakeChatModel fakeChatModel() {
				return new FakeChatModel();
			}
		}

		/** Streams "Hello" + " there" and remembers the last prompt it got. */
		static class FakeChatModel implements ChatModel {
			volatile Prompt lastPrompt;

			@Override
			public ChatResponse call(Prompt prompt) {
				throw new UnsupportedOperationException("the advisor streams");
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				lastPrompt = prompt;
				return Flux.just("Hello", " there").map(text ->
						new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
			}
		}

		@Autowired AdvisorOrchestrator orchestrator;
		@Autowired ConversationService conversations;
		@Autowired FakeChatModel model;

		@Test
		void streamsTheReplyThenSavesIt() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "Hi").collectList().block();

			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "text", "done");
			assertThat(conversations.history(userId)).last().extracting(Message::getText).isEqualTo("Hello there");
		}

		@Test
		void theAdvisorSeesTheEarlierConversationAndTheProfile() {
			UUID userId = UUID.randomUUID();
			orchestrator.send(userId, "I earn 2350 a month").collectList().block();

			orchestrator.send(userId, "What do you know about me?").collectList().block();

			String prompt = model.lastPrompt.getContents();
			assertThat(prompt)
					.contains(ConversationService.GREETING)
					.contains("I earn 2350 a month")
					.contains("Hello there")
					.contains("What the user's profile says so far")
					.endsWith("What do you know about me?");
		}
	}
}
