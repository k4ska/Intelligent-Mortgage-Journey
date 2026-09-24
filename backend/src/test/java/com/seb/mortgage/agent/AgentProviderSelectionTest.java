package com.seb.mortgage.agent;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that AI_PROVIDER (spring.ai.model.chat) plus the matching API key picks the
 * right LLM backend, and that a missing key falls back to the stub. The keys are dummies:
 * nothing here calls a real API.
 */
class AgentProviderSelectionTest {

	@Nested
	@SpringBootTest(properties = "spring.ai.anthropic.api-key=sk-ant-dummy")
        @SuppressWarnings("unused")
	class AnthropicByDefault {

		@Autowired AgentService agentService;
		@Autowired ChatModel chatModel;

		@SuppressWarnings("removal")
		@Test
		void usesClaude() {
			assertThat(agentService.mode()).isEqualTo("claude");
			assertThat(chatModel).isInstanceOf(AnthropicChatModel.class);
			assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("claude-sonnet-5");
			assertThat(chatModel.getDefaultOptions().getTemperature()).isEqualTo(0.3);
		}
	}

	@Nested
	@SpringBootTest(properties = {
			"spring.ai.model.chat=openai",
			"spring.ai.openai.api-key=sk-dummy",
			"OPENAI_MODEL=gpt-test-model" })
        @SuppressWarnings("unused")
	class OpenAiWhenSelected {

		@Autowired AgentService agentService;
		@Autowired ChatModel chatModel;

		@SuppressWarnings("removal")
		@Test
		void usesOpenAi() {
			assertThat(agentService.mode()).isEqualTo("openai");
			assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
			assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("gpt-test-model");
			// Needed so streamed chat replies report token usage (see TokenUsageAdvisor).
			assertThat(((OpenAiChatOptions) chatModel.getDefaultOptions()).getStreamOptions().includeUsage())
					.isTrue();
		}
	}

	@Nested
	@SpringBootTest(properties = {
			"spring.ai.model.chat=openai",
			"spring.ai.anthropic.api-key=sk-ant-dummy" })
        @SuppressWarnings("unused")
	class OpenAiSelectedWithoutItsKey {

		@Autowired AgentService agentService;

		@Test
		void fallsBackToStub() {
			assertThat(agentService.mode()).isEqualTo("stub");
		}
	}
}
