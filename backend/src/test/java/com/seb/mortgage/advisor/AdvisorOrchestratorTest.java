package com.seb.mortgage.advisor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;
import com.seb.mortgage.profile.ProfileService;

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

		/**
		 * Streams "Hello" + " there" and remembers the last prompt it got. For a message
		 * containing {@link #INVENTS}, it replays #88: a first round that writes a reply and
		 * calls updateProfile with values the user never gave, then a second round with the
		 * real reply.
		 */
		static class FakeChatModel implements ChatModel {
			volatile Prompt lastPrompt;

			/** Like OpenAI's: options that let ChatClient run the tool loop. */
			@Override
			public ChatOptions getOptions() {
				return ToolCallingChatOptions.builder().build();
			}

			@Override
			public ChatResponse call(Prompt prompt) {
				throw new UnsupportedOperationException("the advisor streams");
			}

			static final String INVENTS = "only a house";

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				lastPrompt = prompt;
				List<org.springframework.ai.chat.messages.Message> messages = prompt.getInstructions();
				if (messages.getLast().getMessageType() == MessageType.TOOL) {
					return Flux.just(chunk("round-2", "Got it. "), chunk("round-2", "How much do you earn per month?"));
				}
				if (prompt.getContents().endsWith(INVENTS)) {
					AssistantMessage toolCall = AssistantMessage.builder().content("")
							.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "updateProfile",
									"{\"fields\":[{\"key\":\"savings\",\"value\":\"0\"},"
											+ "{\"key\":\"netIncome\",\"value\":\"3300\"}]}")))
							.build();
					return Flux.just(chunk("round-1", "Thanks, I've saved your savings as €0."),
							new ChatResponse(List.of(new Generation(toolCall)),
									ChatResponseMetadata.builder().id("round-1").build()));
				}
				return Flux.just("Hello", " there").map(text ->
						new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
			}

			private static ChatResponse chunk(String roundId, String text) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
						ChatResponseMetadata.builder().id(roundId).build());
			}
		}

		@Autowired AdvisorOrchestrator orchestrator;
		@Autowired ConversationService conversations;
		@Autowired FakeChatModel model;
		@Autowired ProfileService profile;

		@Test
		void streamsTheReplyThenSavesIt() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "Hi").collectList().block();

			// The reply arrives in one piece once the turn is over (only then is it known to be the last round).
			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(conversations.history(userId)).last().extracting(Message::getText).isEqualTo("Hello there");
		}

		/** #88, end to end through Spring AI's real tool loop. */
		@Test
		void aTurnWithToolsGivesOneReplyAndSavesNothingTheUserDidNotSay() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "It's " + FakeChatModel.INVENTS).collectList().block();

			assertThat(events).filteredOn(e -> e.type().equals("text")).extracting(e -> e.data().get("delta"))
					.containsExactly("Got it. How much do you earn per month?");
			assertThat(events).extracting(StreamEvent::type).doesNotContain("profile_updated");
			assertThat(profile.fields(userId)).isEmpty();
			assertThat(conversations.history(userId)).last().extracting(Message::getText)
					.isEqualTo("Got it. How much do you earn per month?");
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
					.contains("What the user's profile and property say so far")
					.endsWith("What do you know about me?");
		}
	}
}
