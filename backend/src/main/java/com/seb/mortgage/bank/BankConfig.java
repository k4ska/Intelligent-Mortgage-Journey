package com.seb.mortgage.bank;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.seb.mortgage.agent.AgentConfig;
import com.seb.mortgage.agent.LlmConfiguredCondition;

/** The negotiation reader exists only with an API key; without one, {@link Negotiator} uses keywords. */
@Configuration
@SuppressWarnings("unused")
class BankConfig {

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	NegotiationAgent negotiationAgent(ChatClient.Builder chatClientBuilder,
			@Value("${app.agent.reasoning-effort.negotiation:}") String reasoningEffort) {
		return new NegotiationAgent(AgentConfig.withReasoningEffort(chatClientBuilder, reasoningEffort));
	}
}
