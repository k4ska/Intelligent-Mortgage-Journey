package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.AgentConfig;
import com.seb.mortgage.agent.LlmConfiguredCondition;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** The advisor agent exists only with an API key; without one the orchestrator gives a canned reply. */
@Configuration
@SuppressWarnings("unused")
class AdvisorConfig {

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	AdvisorAgent advisorAgent(ChatClient.Builder chatClientBuilder,
			@Value("${app.agent.reasoning-effort.advisor:}") String reasoningEffort) {
		return new AdvisorAgent(AgentConfig.withReasoningEffort(chatClientBuilder, reasoningEffort));
	}
}
