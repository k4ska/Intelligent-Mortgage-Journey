package com.seb.mortgage.agent;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that an OpenAI API key switches the agents to OpenAI, and that a missing key
 * falls back to the stub. The key is a dummy: nothing here calls a real API.
 */
class AgentProviderSelectionTest {

	@Nested
	@SpringBootTest(properties = {
			"spring.ai.openai.api-key=sk-dummy",
			"OPENAI_MODEL=gpt-test-model" })
        @SuppressWarnings("unused")
	class OpenAiWithKey {

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
	@SpringBootTest(properties = "spring.ai.openai.api-key=")
        @SuppressWarnings("unused")
	class WithoutKey {

		@Autowired AgentService agentService;

		@Test
		void fallsBackToStub() {
			assertThat(agentService.mode()).isEqualTo("stub");
		}
	}
}
