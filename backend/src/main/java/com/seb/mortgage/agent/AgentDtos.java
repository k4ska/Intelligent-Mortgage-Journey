package com.seb.mortgage.agent;

import java.util.List;

/**
 * Contract 2 of 3 (see README): request/response shapes for the agent endpoints.
 *
 * <pre>
 * POST /api/agent/explain    { applicationId, topic | fieldKey }  -> ExplainResponse
 * POST /api/agent/validate   { applicationId, stepId }            -> ValidateResponse
 * POST /api/agent/review     { applicationId }                    -> ReviewResponse
 * POST /api/agent/chat       { applicationId, message }           -> text/event-stream of tokens
 * </pre>
 */
public final class AgentDtos {

	private AgentDtos() {
	}

	public record ExplainRequest(String applicationId, String topic, String fieldKey) {}

	public record ExplainResponse(String explanation, List<String> relatedTopics) {}

	public record ValidateRequest(String applicationId, String stepId) {}

	public enum ValidationStatus { OK, WARNING, BLOCKER }

	public record ValidationIssue(String fieldKey, ValidationStatus severity, String message) {}

	public record ValidateResponse(ValidationStatus status, List<ValidationIssue> issues) {}

	public record ReviewRequest(String applicationId) {}

	public record ReviewResponse(String summary, List<String> concerns) {}

	public record ChatRequest(String applicationId, String message) {}
}
