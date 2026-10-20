package com.seb.mortgage.advisor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
		 * calls updateProfile with values the user never gave. For {@link #SAVES_THEN_CHECKS}
		 * it saves the income, then checks affordability in the next round (#94); for
		 * {@link #NEVER_STOPS} it calls a tool every round. Reply ({@code stream}): the reply
		 * written after the tools ran. For {@link #MAKES_UP} it answers without tools with a
		 * payment nobody gave.
		 * It also plays the verifier (#67), answering with {@link #verdict}. It remembers the
		 * prompt of each call.
		 */
		static class FakeChatModel implements ChatModel {
			static final String INVENTS = "only a house";
			static final String INVENTED_TEXT = "Thanks, I've saved your savings as €0.";
			static final String SAVES_THEN_CHECKS = "I earn 2350 a month, can I afford it?";
			static final String NEVER_STOPS = "check it again and again";
			static final String MAKES_UP = "What would I pay a month?";
			static final String MADE_UP_REPLY = "You would pay about €1,234 a month.";
			static final String APPROVE = "{\"approved\":true,\"rewrite\":null}";
			static final String SAFE_REPLY = "I can't give a monthly payment yet. What is your net income?";
			static final String REJECT = "{\"approved\":false,\"rewrite\":\"" + SAFE_REPLY + "\"}";
			/** Makes the verifier's call fail. */
			static final String BROKEN = "broken";

			volatile String verdict = APPROVE;
			final AtomicInteger verifierCalls = new AtomicInteger();

			/** Every act call of the last turn, in order. */
			final List<Prompt> actPrompts = new CopyOnWriteArrayList<>();
			volatile Prompt actPrompt;
			volatile Prompt replyPrompt;

			/** Like OpenAI's: options that can carry tools. */
			@Override
			public ChatOptions getOptions() {
				return ToolCallingChatOptions.builder().build();
			}

			@Override
			public ChatResponse call(Prompt prompt) {
				List<org.springframework.ai.chat.messages.Message> messages = prompt.getInstructions();
				if (messages.stream().anyMatch(m -> m instanceof SystemMessage
						&& m.getText().contains("quality-and-safety checker"))) {
					verifierCalls.incrementAndGet();
					if (verdict.equals(BROKEN)) {
						throw new IllegalStateException("verifier down");
					}
					return new ChatResponse(List.of(new Generation(new AssistantMessage(verdict))));
				}
				int lastUser = 0;
				for (int i = 0; i < messages.size(); i++) {
					if (messages.get(i) instanceof UserMessage) {
						lastUser = i;
					}
				}
				if (lastUser == messages.size() - 1) {
					actPrompts.clear();
				}
				actPrompt = prompt;
				actPrompts.add(prompt);
				String user = messages.get(lastUser).getText();
				// Earlier turns have no tool messages, so these are this turn's rounds so far.
				long round = messages.subList(lastUser, messages.size()).stream()
						.filter(ToolResponseMessage.class::isInstance).count() + 1;

				if (user.endsWith(INVENTS) && round == 1) {
					return toolCall(INVENTED_TEXT, "updateProfile",
							"{\"fields\":[{\"key\":\"savings\",\"value\":\"0\",\"quote\":\"only a house\"},"
									+ "{\"key\":\"netIncome\",\"value\":\"3300\",\"quote\":\"only a house\"}]}");
				}
				if (user.equals(SAVES_THEN_CHECKS) && round == 1) {
					return toolCall("Saving that.", "updateProfile",
							"{\"fields\":[{\"key\":\"netIncome\",\"value\":\"2350\",\"quote\":\"I earn 2350 a month\"}]}");
				}
				if (user.equals(SAVES_THEN_CHECKS) && round == 2) {
					return toolCall("Now checking.", "checkAffordability", "{}");
				}
				if (user.equals(MAKES_UP)) {
					return new ChatResponse(List.of(new Generation(new AssistantMessage(MADE_UP_REPLY))));
				}
				if (user.equals(NEVER_STOPS)) {
					return toolCall("Again.", "checkAffordability", "{}");
				}
				return new ChatResponse(List.of(new Generation(new AssistantMessage(round == 1 ? "Hello there" : "Done."))));
			}

			private static ChatResponse toolCall(String text, String tool, String arguments) {
				AssistantMessage call = AssistantMessage.builder().content(text)
						.toolCalls(List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool, arguments)))
						.build();
				return new ChatResponse(List.of(new Generation(call)));
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

		@BeforeEach
		void approveByDefault() {
			model.verdict = FakeChatModel.APPROVE;
			model.verifierCalls.set(0);
		}

		@Test
		void aTurnWithoutToolsIsOneCall() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, "Hi").collectList().block();

			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(conversations.history(userId)).last().extracting(Message::getText).isEqualTo("Hello there");
			// No figures and no money tools: the verifier isn't asked (#67).
			assertThat(model.verifierCalls).hasValue(0);
		}

		/** #67: a figure from nowhere sends the reply to the verifier, whose safe version replaces it. */
		@Test
		void aMadeUpNumberIsCorrected() {
			UUID userId = UUID.randomUUID();
			model.verdict = FakeChatModel.REJECT;

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.MAKES_UP).collectList().block();

			assertThat(model.verifierCalls).hasValue(1);
			Message saved = conversations.history(userId).getLast();
			assertThat(saved.getText()).isEqualTo(FakeChatModel.SAFE_REPLY);
			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "corrected", "done");
			assertThat(events.get(1)).isEqualTo(StreamEvent.corrected(saved.getId(), FakeChatModel.SAFE_REPLY));
			assertThat(events.get(2)).isEqualTo(StreamEvent.done(saved.getId()));
		}

		@Test
		void anApprovedReplyStands() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.MAKES_UP).collectList().block();

			assertThat(model.verifierCalls).hasValue(1);
			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(conversations.history(userId).getLast().getText()).isEqualTo(FakeChatModel.MADE_UP_REPLY);
		}

		@Test
		void aBrokenVerifierDoesNotBreakTheTurn() {
			UUID userId = UUID.randomUUID();
			model.verdict = FakeChatModel.BROKEN;

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.MAKES_UP).collectList().block();

			assertThat(model.verifierCalls).hasPositiveValue();
			assertThat(events).extracting(StreamEvent::type).containsExactly("text", "done");
			assertThat(conversations.history(userId).getLast().getText()).isEqualTo(FakeChatModel.MADE_UP_REPLY);
		}

		/** A turn that checked affordability is verified even when its reply has no figures. */
		@Test
		void aMoneyTurnIsAlwaysVerified() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.SAVES_THEN_CHECKS).collectList().block();

			assertThat(model.verifierCalls).hasValue(1);
			assertThat(events).extracting(StreamEvent::type).doesNotContain("corrected").endsWith("done");
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

		/** #94: a later round sees what the earlier one's tools returned, so it can save, then check. */
		@Test
		void aTurnCanSaveAFactThenCheckWithIt() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.SAVES_THEN_CHECKS).collectList().block();

			assertThat(events).extracting(StreamEvent::type).contains("profile_updated");
			assertThat(profile.fields(userId)).extracting(f -> f.key() + "=" + f.value()).containsExactly("netIncome=2350");
			// Three act calls: save, check, then one that calls nothing.
			assertThat(model.actPrompts).hasSize(3);
			ToolResponseMessage saved = (ToolResponseMessage) model.actPrompts.get(1).getInstructions().getLast();
			assertThat(saved.getResponses()).singleElement().satisfies(response -> {
				assertThat(response.name()).isEqualTo("updateProfile");
				assertThat(response.responseData()).isEqualTo("Saved: netIncome=2350.");
			});
			// The model never sees the text it wrote next to its calls, and neither does the user.
			assertThat(model.actPrompts.get(2).getContents()).doesNotContain("Saving that.", "Now checking.");
			assertThat(model.replyPrompt.getContents())
					.contains("- updateProfile: Saved: netIncome=2350.")
					.contains("- checkAffordability: Can't tell yet. Still missing: savings")
					.doesNotContain("Saving that.", "Now checking.", "Done.");
			assertThat(model.replyPrompt.getInstructions()).extracting(m -> m.getMessageType())
					.doesNotContain(MessageType.TOOL);
			assertThat(conversations.history(userId)).last().extracting(Message::getText)
					.isEqualTo("Got it. How much do you earn per month?");
		}

		@Test
		void aTurnStopsActingAfterTheLastRound() {
			UUID userId = UUID.randomUUID();

			List<StreamEvent> events = orchestrator.send(userId, FakeChatModel.NEVER_STOPS).collectList().block();

			assertThat(model.actPrompts).hasSize(AdvisorAgent.MAX_ACT_ROUNDS);
			assertThat(events).extracting(StreamEvent::type).containsSubsequence("text", "done");
			assertThat(model.replyPrompt.getContents().split("- checkAffordability:", -1))
					.hasSize(AdvisorAgent.MAX_ACT_ROUNDS + 1);
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
