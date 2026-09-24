package com.seb.mortgage.agent;

import com.seb.mortgage.application.ApplicationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
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
	ExplainAgent explainAgent(ChatClient.Builder chatClientBuilder) {
		return new ExplainAgent(chatClientBuilder);
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ValidateAgent validateAgent(ChatClient.Builder chatClientBuilder) {
		return new ValidateAgent(chatClientBuilder);
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ReviewAgent reviewAgent(ChatClient.Builder chatClientBuilder) {
		return new ReviewAgent(chatClientBuilder);
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	ChatAgent chatAgent(ChatClient.Builder chatClientBuilder) {
		return new ChatAgent(chatClientBuilder);
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	VerifierAgent verifierAgent(ChatClient.Builder chatClientBuilder) {
		return new VerifierAgent(chatClientBuilder);
	}

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	AgentService agentOrchestrator(ExplainAgent explainAgent, ValidateAgent validateAgent,
			ReviewAgent reviewAgent, ChatAgent chatAgent, VerifierAgent verifierAgent,
			ApplicationService applicationService, ObjectMapper objectMapper,
			@Value("${spring.ai.model.chat:anthropic}") String provider) {
		// Reported to the frontend as "claude" or "openai" so the UI names the right assistant.
		String mode = provider.trim().equalsIgnoreCase("openai") ? "openai" : "claude";
		return new AgentOrchestrator(explainAgent, validateAgent, reviewAgent, chatAgent,
				verifierAgent, applicationService, objectMapper, mode,
				new StubAgentService(applicationService));
	}

	@Bean
	@ConditionalOnMissingBean(AgentService.class)
	AgentService stubAgentService(ApplicationService applicationService) {
		return new StubAgentService(applicationService);
	}
}
