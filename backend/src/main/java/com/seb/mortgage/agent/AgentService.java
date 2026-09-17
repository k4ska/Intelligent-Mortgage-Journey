package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * The four things the agent does. Two implementations exist:
 * <ul>
 *   <li>{@code StubAgentService} - deterministic canned responses, active by default so
 *       the app works with no API key.</li>
 *   <li>{@code AgentOrchestrator} - routes to specialist agents (one per task) and gates
 *       their output through an independent verifier, active when
 *       {@code spring.ai.anthropic.api-key} is set.</li>
 * </ul>
 */
public interface AgentService {

	ExplainResponse explain(String topicOrFieldKey, UUID applicationId);

	ValidateResponse validate(UUID applicationId, String stepId);

	ReviewResponse review(UUID applicationId);

	Flux<String> chat(UUID applicationId, String message);

	/** Reported to the frontend so the UI can show whether the real agent is wired up. */
	String mode();
}
