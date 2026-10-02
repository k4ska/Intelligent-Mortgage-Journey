package com.seb.mortgage.agent;

import com.seb.mortgage.application.ApplicationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the multi-agent backend. If the selected LLM provider (Anthropic or OpenAI, see
 * {@link LlmConfiguredCondition}) has an API key, Spring AI auto-configures
 * {@link ChatClient.Builder} for it and we build one specialist per agent task
 * (explain / validate / review / chat) plus an independent verifier, and route them
 * through {@link AgentOrchestrator}. Otherwise the deterministic stub is used so the app
 * always starts.
 *
 * <p>Each specialist gets its own {@code ChatClient.Builder} injection (a fresh,
 * prototype-scoped builder per bean) so it can carry its own narrow system prompt,
 * independent of the others.
 *
 * <p>On OpenAI, each agent also gets its own reasoning effort ({@code app.agent.reasoning-effort.*}
 * in {@code application.yml}). GPT-5-family models generate hidden reasoning tokens before
 * answering, and those dominate reply time, so the short structured tasks run with less of
 * it. Anthropic has no equivalent option, so the setting is ignored there.
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
	ExplainAgent explainAgent(ChatClient.Builder chatClientBuilder,
			@Value("${spring.ai.model.chat:anthropic}") String provider,
			@Value("${app.agent.reasoning-effort.explain:}") String reasoningEffort) {
		return new ExplainAgent(withReasoningEffort(chatClientBuilder, provider, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ValidateAgent validateAgent(ChatClient.Builder chatClientBuilder,
			@Value("${spring.ai.model.chat:anthropic}") String provider,
			@Value("${app.agent.reasoning-effort.validate:}") String reasoningEffort) {
		return new ValidateAgent(withReasoningEffort(chatClientBuilder, provider, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ReviewAgent reviewAgent(ChatClient.Builder chatClientBuilder,
			@Value("${spring.ai.model.chat:anthropic}") String provider,
			@Value("${app.agent.reasoning-effort.review:}") String reasoningEffort) {
		return new ReviewAgent(withReasoningEffort(chatClientBuilder, provider, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ChatAgent chatAgent(ChatClient.Builder chatClientBuilder,
			@Value("${spring.ai.model.chat:anthropic}") String provider,
			@Value("${app.agent.reasoning-effort.chat:}") String reasoningEffort) {
		return new ChatAgent(withReasoningEffort(chatClientBuilder, provider, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	VerifierAgent verifierAgent(ChatClient.Builder chatClientBuilder,
			@Value("${spring.ai.model.chat:anthropic}") String provider,
			@Value("${app.agent.reasoning-effort.verifier:}") String reasoningEffort) {
		return new VerifierAgent(withReasoningEffort(chatClientBuilder, provider, reasoningEffort));
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	AgentService agentOrchestrator(ExplainAgent explainAgent, ValidateAgent validateAgent,
			ReviewAgent reviewAgent, ChatAgent chatAgent, VerifierAgent verifierAgent,
			ApplicationService applicationService, ObjectMapper objectMapper,
			@Value("${spring.ai.model.chat:anthropic}") String provider) {
		// Reported to the frontend as "claude" or "openai" so the UI names the right assistant.
		String mode = isOpenAi(provider) ? "openai" : "claude";
		return new AgentOrchestrator(explainAgent, validateAgent, reviewAgent, chatAgent,
				verifierAgent, applicationService, objectMapper, mode,
				new StubAgentService(applicationService));
	}

	/**
	 * Sets the agent's reasoning effort (e.g. {@code minimal}, {@code low}, {@code medium},
	 * {@code high}) when the provider is OpenAI and a value is configured. Only that one
	 * option is set; the model and everything else still come from
	 * {@code spring.ai.openai.chat.options}, which Spring AI merges underneath.
	 */
	static ChatClient.Builder withReasoningEffort(ChatClient.Builder builder, String provider,
			String reasoningEffort) {
		if (!isOpenAi(provider) || !StringUtils.hasText(reasoningEffort)) {
			return builder;
		}
		return builder.defaultOptions(OpenAiChatOptions.builder().reasoningEffort(reasoningEffort.trim()));
	}

	private static boolean isOpenAi(String provider) {
		return provider.trim().equalsIgnoreCase("openai");
	}

	@Bean
	@ConditionalOnMissingBean(AgentService.class)
	AgentService stubAgentService(ApplicationService applicationService) {
		return new StubAgentService(applicationService);
	}
}
