package com.seb.mortgage.agent;

/**
 * Thrown by {@link TokenUsageAdvisor} instead of calling the LLM once today's token
 * budget is spent. The orchestrators catch it and answer with their canned stub reply.
 */
public class TokenBudgetExceededException extends RuntimeException {

	public TokenBudgetExceededException() {
		super("Daily token budget reached");
	}
}
