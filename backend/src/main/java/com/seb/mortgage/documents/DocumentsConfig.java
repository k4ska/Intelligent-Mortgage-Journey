package com.seb.mortgage.documents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.seb.mortgage.agent.AgentConfig;
import com.seb.mortgage.agent.LlmConfiguredCondition;

/** The document reader agent exists only with an API key; without one nothing is read from documents. */
@Configuration
@SuppressWarnings("unused")
class DocumentsConfig {

	@Bean
	@Conditional(LlmConfiguredCondition.class)
	DocumentReaderAgent documentReaderAgent(ChatClient.Builder chatClientBuilder,
			@Value("${app.agent.reasoning-effort.document-reader:}") String reasoningEffort) {
		return new DocumentReaderAgent(AgentConfig.withReasoningEffort(chatClientBuilder, reasoningEffort));
	}
}
