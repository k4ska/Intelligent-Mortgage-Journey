package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the shared agent infrastructure. If an OpenAI API key is configured (see
 * {@link LlmConfiguredCondition}), Spring AI auto-configures {@link ChatClient.Builder}
 * for it and the agents are built on it; otherwise the app runs in stub mode, so it
 * always starts.
 *
 * <p>The form-era specialists are gone (see {@code docs/decisions.md}, 2026-10-15). What
 * stays is what every agent of the chat-based advisor builds on: token counting with the
 * daily budget, the shared {@link VerifierAgent}, and per-agent reasoning effort. The
 * advisor and bank agents add their own beans here (or in their own packages).
 *
 * <p>Each agent gets its own {@code ChatClient.Builder} injection (a fresh,
 * prototype-scoped builder per bean) so it can carry its own narrow system prompt, and its
 * own reasoning effort ({@code app.agent.reasoning-effort.*} in {@code application.yml}).
 * GPT-5-family models generate hidden reasoning tokens before answering, and those
 * dominate reply time, so short structured tasks run with less of it.
 */
@Configuration
@SuppressWarnings("unused")
public class AgentConfig {

	/** Adds token counting and the daily budget check to every ChatClient we build. */
	@Bean
	ChatClientBuilderCustomizer tokenUsageCustomizer(TokenUsageTracker tracker) {
		return builder -> builder.defaultAdvisors(new TokenUsageAdvisor(tracker));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	VerifierAgent verifierAgent(ChatClient.Builder chatClientBuilder,
			@Value("${app.agent.reasoning-effort.verifier:}") String reasoningEffort) {
		return new VerifierAgent(withReasoningEffort(chatClientBuilder, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	AgentMode openAiMode() {
		return new AgentMode("openai");
	}

	@Bean
	@ConditionalOnMissingBean(AgentMode.class)
	AgentMode stubMode() {
		return new AgentMode("stub");
	}

	/**
	 * Sets the agent's reasoning effort (e.g. {@code minimal}, {@code low}, {@code medium},
	 * {@code high}) when a value is configured. Only that one option is set; the model and
	 * everything else still come from
	 * {@code spring.ai.openai.chat.options}, which Spring AI merges underneath.
	 */
	static ChatClient.Builder withReasoningEffort(ChatClient.Builder builder, String reasoningEffort) {
		if (!StringUtils.hasText(reasoningEffort)) {
			return builder;
		}
		return builder.defaultOptions(OpenAiChatOptions.builder().reasoningEffort(reasoningEffort.trim()));
	}
}
