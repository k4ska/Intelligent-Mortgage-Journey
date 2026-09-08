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
 * Picks the agent implementation. If an Anthropic API key is configured, Spring AI
 * auto-configures {@link ChatClient.Builder} and we use the real Claude-backed service;
 * otherwise the deterministic stub is used so the app always starts.
 */
@Configuration
public class AgentConfig {

	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
	@ConditionalOnBean(ChatClient.Builder.class)
	AgentService claudeAgentService(ChatClient.Builder chatClientBuilder,
			ApplicationService applicationService,
			ObjectMapper objectMapper) {
		return new ClaudeAgentService(chatClientBuilder, applicationService, objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean(AgentService.class)
	AgentService stubAgentService(ApplicationService applicationService) {
		return new StubAgentService(applicationService);
	}
}
