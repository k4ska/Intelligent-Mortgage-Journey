package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import com.seb.mortgage.agent.AgentDtos.ValidationIssue;
import com.seb.mortgage.agent.AgentDtos.ValidationStatus;
import com.seb.mortgage.application.ApplicationService;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Routes each agent request to its specialist ({@link ExplainAgent}, {@link
 * ValidateAgent}, {@link ReviewAgent}, {@link ChatAgent}), then runs the specialist's
 * draft past an independent {@link VerifierAgent} before handing it back to the client.
 *
 * <p>{@code chat} is the exception: it streams token-by-token, so there is nothing
 * complete to verify before the first tokens reach the user. See
 * {@code docs/decisions.md} for that tradeoff.
 */
public class AgentOrchestrator implements AgentService {

	private final ExplainAgent explainAgent;
	private final ValidateAgent validateAgent;
	private final ReviewAgent reviewAgent;
	private final ChatAgent chatAgent;
	private final VerifierAgent verifier;
	private final ApplicationService applicationService;
	private final ObjectMapper objectMapper;
	private final String mode;
	/** Answers instead of the LLM once today's token budget is spent. */
	private final StubAgentService budgetFallback;

	public AgentOrchestrator(ExplainAgent explainAgent, ValidateAgent validateAgent,
			ReviewAgent reviewAgent, ChatAgent chatAgent, VerifierAgent verifier,
			ApplicationService applicationService, ObjectMapper objectMapper, String mode,
			StubAgentService budgetFallback) {
		this.explainAgent = explainAgent;
		this.validateAgent = validateAgent;
		this.reviewAgent = reviewAgent;
		this.chatAgent = chatAgent;
		this.verifier = verifier;
		this.applicationService = applicationService;
		this.objectMapper = objectMapper;
		this.mode = mode;
		this.budgetFallback = budgetFallback;
	}

	@Override
	public String mode() {
		return mode;
	}

	@Override
	public ExplainResponse explain(String topicOrFieldKey, UUID applicationId) {
		try {
			return explainWithLlm(topicOrFieldKey, applicationId);
		} catch (TokenBudgetExceededException e) {
			return budgetFallback.explain(topicOrFieldKey, applicationId);
		}
	}

	private ExplainResponse explainWithLlm(String topicOrFieldKey, UUID applicationId) {
		String profileJson = profileJson(applicationId);
		ExplainResponse draft = explainAgent.explain(topicOrFieldKey, profileJson);
		VerifierAgent.Verdict verdict = verifier.verify(
				"Plain-language explanation of \"" + topicOrFieldKey + "\" for the applicant",
				draft.explanation(), profileJson);
		return new ExplainResponse(verdict.output(), draft.relatedTopics(), draft.followUpQuestion());
	}

	@Override
	public ValidateResponse validate(UUID applicationId, String stepId) {
		try {
			return validateWithLlm(applicationId, stepId);
		} catch (TokenBudgetExceededException e) {
			return budgetFallback.validate(applicationId, stepId);
		}
	}

	private ValidateResponse validateWithLlm(UUID applicationId, String stepId) {
		String profileJson = profileJson(applicationId);
		ValidateResponse draft = validateAgent.validate(stepId, profileJson);
		if (draft.issues().isEmpty()) {
			return draft;
		}

		String combined = draft.issues().stream()
				.map(ValidationIssue::message)
				.collect(Collectors.joining("\n"));
		VerifierAgent.Verdict verdict = verifier.verify(
				"Application validation issues raised for step \"" + stepId + "\"", combined, profileJson);
		if (verdict.approved()) {
			return draft;
		}
		return new ValidateResponse(ValidationStatus.WARNING,
				List.of(new ValidationIssue(null, ValidationStatus.WARNING, verdict.output())),
				draft.followUpQuestion());
	}

	@Override
	public ReviewResponse review(UUID applicationId) {
		try {
			return reviewWithLlm(applicationId);
		} catch (TokenBudgetExceededException e) {
			return budgetFallback.review(applicationId);
		}
	}

	private ReviewResponse reviewWithLlm(UUID applicationId) {
		String profileJson = profileJson(applicationId);
		ReviewResponse draft = reviewAgent.review(profileJson);

		String combined = draft.summary() + "\n" + String.join("\n", draft.concerns());
		VerifierAgent.Verdict verdict = verifier.verify(
				"Final read-back summary of the completed application", combined, profileJson);
		if (verdict.approved()) {
			return draft;
		}
		return new ReviewResponse(verdict.output(), draft.concerns(), draft.followUpQuestion());
	}

	@Override
	public Flux<String> chat(UUID applicationId, String message) {
		return chatAgent.chat(message, profileJson(applicationId))
				.onErrorResume(TokenBudgetExceededException.class,
						e -> budgetFallback.chat(applicationId, message));
	}

	private String profileJson(UUID applicationId) {
		try {
			return objectMapper.writeValueAsString(applicationService.profile(applicationId));
		} catch (Exception e) {
			return "{}";
		}
	}
}
