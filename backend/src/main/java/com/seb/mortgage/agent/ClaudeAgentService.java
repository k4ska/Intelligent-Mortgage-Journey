package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import com.seb.mortgage.application.ApplicationService;
import com.seb.mortgage.profile.LoanProfile;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Real agent backed by Claude via Spring AI. Only instantiated when
 * {@code spring.ai.anthropic.api-key} is configured (see {@code AgentConfig}).
 *
 * <p>The design keeps Claude on a short leash: every call is a single request with the
 * application's derived {@link LoanProfile} supplied as context, plus the shared
 * {@link Explanations} vocabulary. There is no autonomous tool-calling loop.
 */
public class ClaudeAgentService implements AgentService {

	private static final String SYSTEM = """
			You are a calm, plain-language mortgage guide inside an application journey.
			You explain, you sanity-check, you never give regulated financial advice and you
			never tell the user which product to choose. Keep answers short and concrete, use
			the user's own numbers when they are provided, and prefer everyday words over
			jargon. If you are unsure, say so.
			""";

	private final ChatClient chatClient;
	private final ApplicationService applicationService;
	private final ObjectMapper objectMapper;

	public ClaudeAgentService(ChatClient.Builder chatClientBuilder,
			ApplicationService applicationService,
			ObjectMapper objectMapper) {
		this.chatClient = chatClientBuilder.defaultSystem(SYSTEM).build();
		this.applicationService = applicationService;
		this.objectMapper = objectMapper;
	}

	@Override
	public String mode() {
		return "claude";
	}

	@Override
	public ExplainResponse explain(String topicOrFieldKey, UUID applicationId) {
		Explanations.Entry reference = Explanations.lookup(topicOrFieldKey);
		String referenceText = reference == null ? "(no canned reference for this topic)" : reference.text();
		String profileJson = profileJson(applicationId);

		String explanation = chatClient.prompt()
				.user(u -> u.text("""
						Explain the mortgage concept "{topic}" to this applicant in 2-4 sentences.
						Use their numbers where relevant.

						Reference wording to stay consistent with:
						{reference}

						Applicant profile (JSON, may be partial):
						{profile}
						""")
						.param("topic", topicOrFieldKey)
						.param("reference", referenceText)
						.param("profile", profileJson))
				.call()
				.content();

		List<String> related = reference == null ? List.of() : reference.related();
		return new ExplainResponse(explanation, related);
	}

	@Override
	public ValidateResponse validate(UUID applicationId, String stepId) {
		String profileJson = profileJson(applicationId);
		return chatClient.prompt()
				.user(u -> u.text("""
						Review this in-progress mortgage application for step "{step}".
						Point out anything that would weaken it with a lender - high loan-to-value,
						a failed or tight rate-rise stress test, high debt-to-income, thin buffers.
						Be specific and friendly. If nothing stands out, return status OK with no issues.

						Applicant profile (JSON):
						{profile}
						""")
						.param("step", stepId)
						.param("profile", profileJson))
				.call()
				.entity(ValidateResponse.class);
	}

	@Override
	public ReviewResponse review(UUID applicationId) {
		String profileJson = profileJson(applicationId);
		return chatClient.prompt()
				.user(u -> u.text("""
						Read this completed mortgage application back to the user in one short paragraph
						of plain language, then list the main concerns a lender might raise as short
						bullet strings. Do not recommend a product.

						Applicant profile (JSON):
						{profile}
						""")
						.param("profile", profileJson))
				.call()
				.entity(ReviewResponse.class);
	}

	@Override
	public Flux<String> chat(UUID applicationId, String message) {
		String profileJson = profileJson(applicationId);
		return chatClient.prompt()
				.user(u -> u.text("""
						The user is part-way through a mortgage application. Answer their question
						using their profile below. Educational, not advisory.

						Profile (JSON): {profile}

						Question: {question}
						""")
						.param("profile", profileJson)
						.param("question", message))
				.stream()
				.content();
	}

	private String profileJson(UUID applicationId) {
		try {
			return objectMapper.writeValueAsString(applicationService.profile(applicationId));
		} catch (Exception e) {
			return "{}";
		}
	}
}
