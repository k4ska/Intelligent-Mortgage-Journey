package com.seb.mortgage.agent;

/**
 * Thrown by {@link TokenUsageAdvisor} instead of calling the LLM once today's token
 * budget is spent. {@link AgentOrchestrator} catches it and answers with the stub.
 */
public class TokenBudgetExceededException extends RuntimeException {

	public TokenBudgetExceededException() {
		super("Daily token budget reached");
	}
}
