package com.seb.mortgage.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * {@link AgentConfig#withReasoningEffort} should add the reasoning effort on OpenAI
 * without dropping the model configured in application.yml, and do nothing otherwise.
 */
class ReasoningEffortTest {

	/** Stands in for OpenAiChatModel: carries the yml defaults and records each request. */
	static class RecordingOpenAiModel implements ChatModel {
		final AtomicReference<ChatOptions> lastOptions = new AtomicReference<>();

		@Override
		public ChatOptions getOptions() {
			return OpenAiChatOptions.builder().model("gpt-5-mini").build();
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			lastOptions.set(prompt.getOptions());
			return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
		}
	}

	private static ChatOptions send(String provider, String effort) {
		var model = new RecordingOpenAiModel();
		AgentConfig.withReasoningEffort(ChatClient.builder(model), provider, effort)
				.build().prompt().user("hi").call().content();
		return model.lastOptions.get();
	}

	@Test
	void setsReasoningEffortOnOpenAiAndKeepsTheModel() {
		var options = (OpenAiChatOptions) send("openai", "low");

		assertThat(options.getReasoningEffort()).isEqualTo("low");
		assertThat(options.getModel()).isEqualTo("gpt-5-mini");
	}

	@Test
	void leavesOptionsAloneForOtherProvidersOrBlankValues() {
		assertThat(((OpenAiChatOptions) send("anthropic", "low")).getReasoningEffort()).isNull();
		assertThat(((OpenAiChatOptions) send("openai", "")).getReasoningEffort()).isNull();
	}
}
