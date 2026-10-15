package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Shared base for every backend agent (the specialists and the verifier alike): builds
 * one {@link ChatClient} off a fresh, prototype-scoped {@code ChatClient.Builder} with
 * the agent's own system prompt, and nothing else.
 *
 * <p>Kept deliberately thin - no shared state, no shared prompt text - so each agent's
 * behaviour is fully defined by its own system prompt and its own method(s). Agents that
 * use tools (like the advisor) pass them per request with {@code .tools(...)}, because a
 * tool usually acts for one user. This is the one thing every agent in the codebase has in
 * common; see {@code docs/architecture.md} for how the agents fit together.
 */
public abstract class LlmSpecialistAgent {

	protected final ChatClient chatClient;

	protected LlmSpecialistAgent(ChatClient.Builder chatClientBuilder, String systemPrompt) {
		this.chatClient = chatClientBuilder
				.defaultSystem(systemPrompt)
				// Names the agent in the token-usage log (see TokenUsageAdvisor).
				.defaultAdvisors(a -> a.param(TokenUsageAdvisor.AGENT, getClass().getSimpleName()))
				.build();
	}
}
