package com.seb.mortgage.advisor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
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
		 * Plays the model in both steps of a turn. Act ({@code call}): answers "Hello there",
		 * or, for a message ending in {@link #INVENTS}, replays #88: it writes a reply and
		 * calls updateProfile with values the user never gave. Reply ({@code stream}): the
		 * reply written after the tools ran. It remembers the prompt of each step.
		 */
		static class FakeChatModel implements ChatModel {
			static final String INVENTS = "only a house";
			static final String INVENTED_TEXT = "Thanks, I've saved your savings as €0.";

			volatile Prompt actPrompt;
			volatile Prompt replyPrompt;

			/** Like OpenAI's: options that can carry tools. */
			@Override
			public ChatOptions getOptions() {
				return ToolCallingChatOptions.builder().build();
			}

			@Override
			public ChatResponse call(Prompt prompt) {
				actPrompt = prompt;
				if (!prompt.getContents().endsWith(INVENTS)) {
					return new ChatResponse(List.of(new Generation(new AssistantMessage("Hello there"))));
				}
				AssistantMessage toolCall = AssistantMessage.builder().content(INVENTED_TEXT)
						.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "updateProfile",
								"{\"fields\":[{\"key\":\"savings\",\"value\":\"0\",\"quote\":\"only a house\"},"
										+ "{\"key\":\"netIncome\",\"value\":\"3300\",\"quote\":\"only a house\"}]}")))
						.build();
				return new ChatResponse(List.of(new Generation(toolCall)));
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				replyPrompt = prompt;
				return Flux.just("Got it. ", "How much do you earn per month?").map(text ->
						new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
			}
		}

		@Autowired AdvisorOrchestrator orchestrator;
		@Autowired ConversationService conversations;
		@Autowired FakeChatModel model;
		@Autowired ProfileService profile;

		@Test
		void aTurnWithoutToolsIsOneCall() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "Hi").collectList().block();

			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(conversations.history(userId)).last().extracting(Message::getText).isEqualTo("Hello there");
		}

		/** #88: act, then a separate reply call that sees what the tools did but no tools. */
		@Test
		void aTurnWithToolsActsThenRepliesWithoutTools() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "It's " + FakeChatModel.INVENTS).collectList().block();

			// The invented values were refused, so nothing was saved and the panel wasn't told anything.
			assertThat(events).extracting(StreamEvent::type).doesNotContain("profile_updated");
			assertThat(profile.fields(userId)).isEmpty();
			// Only the reply step's text reaches the user, streamed; the act step's text is dropped.
			assertThat(events).filteredOn(e -> e.type().equals("text")).extracting(e -> e.data().get("delta"))
					.containsExactly("Got it. ", "How much do you earn per month?");
			assertThat(conversations.history(userId)).last().extracting(Message::getText)
					.isEqualTo("Got it. How much do you earn per month?");
			// The act step had the tools; the reply step has none, only a note of what they did.
			assertThat(((ToolCallingChatOptions) model.actPrompt.getOptions()).getToolCallbacks()).isNotEmpty();
			assertThat(((ToolCallingChatOptions) model.replyPrompt.getOptions()).getToolCallbacks()).isNullOrEmpty();
			assertThat(model.replyPrompt.getInstructions()).extracting(m -> m.getMessageType())
					.doesNotContain(MessageType.TOOL);
			assertThat(model.replyPrompt.getContents())
					.contains("savings=0 was not saved because the user never said it")
					.doesNotContain(FakeChatModel.INVENTED_TEXT);
		}

		@Test
		void theAdvisorSeesTheEarlierConversationAndTheProfile() {
			UUID userId = UUID.randomUUID();
			orchestrator.send(userId, "I earn 2350 a month").collectList().block();

			orchestrator.send(userId, "What do you know about me?").collectList().block();

			String prompt = model.actPrompt.getContents();
			assertThat(prompt)
					.contains(ConversationService.GREETING)
					.contains("I earn 2350 a month")
					.contains("Hello there")
					.contains("What the user's profile and property say so far")
					.endsWith("What do you know about me?");
		}
	}
}
