package com.seb.mortgage.agent;

import com.seb.mortgage.application.ApplicationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the multi-agent backend. If an Anthropic API key is configured, Spring AI
 * auto-configures {@link ChatClient.Builder} and we build one specialist per agent task
 * (explain / validate / review / chat) plus an independent verifier, and route them
 * through {@link AgentOrchestrator}. Otherwise the deterministic stub is used so the app
 * always starts.
 *
 * <p>Each specialist gets its own {@code ChatClient.Builder} injection (a fresh,
 * prototype-scoped builder per bean) so it can carry its own narrow system prompt,
 * independent of the others.
 */
@Configuration
public class AgentConfig {

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	ExplainAgent explainAgent(ChatClient.Builder chatClientBuilder) {
		return new ExplainAgent(chatClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	ValidateAgent validateAgent(ChatClient.Builder chatClientBuilder) {
		return new ValidateAgent(chatClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	ReviewAgent reviewAgent(ChatClient.Builder chatClientBuilder) {
		return new ReviewAgent(chatClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	ChatAgent chatAgent(ChatClient.Builder chatClientBuilder) {
		return new ChatAgent(chatClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	VerifierAgent verifierAgent(ChatClient.Builder chatClientBuilder) {
		return new VerifierAgent(chatClientBuilder);
	}

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	AgentService agentOrchestrator(ExplainAgent explainAgent, ValidateAgent validateAgent,
			ReviewAgent reviewAgent, ChatAgent chatAgent, VerifierAgent verifierAgent,
			ApplicationService applicationService, ObjectMapper objectMapper) {
		return new AgentOrchestrator(explainAgent, validateAgent, reviewAgent, chatAgent,
				verifierAgent, applicationService, objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean(AgentService.class)
	AgentService stubAgentService(ApplicationService applicationService) {
		return new StubAgentService(applicationService);
	}
}
