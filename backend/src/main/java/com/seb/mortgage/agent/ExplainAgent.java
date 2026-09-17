package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * Specialist agent: explains mortgage concepts and form fields in plain language. Does
 * nothing else - no validation, no read-back, no free chat.
 */
public class ExplainAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are the terminology specialist inside a mortgage application journey. Your
			only job is explaining mortgage concepts and form fields in plain language, in
			2-4 sentences, using the applicant's own numbers when given. You never validate
			the application, never read it back, and never recommend a product or lender.

			If the topic or the applicant's profile is too ambiguous for you to give a good,
			specific explanation, still give your best short answer and set followUpQuestion
			to one short clarifying question. Otherwise leave followUpQuestion null.
			""";

	public ExplainAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	record Draft(String explanation, String followUpQuestion) {}

	public ExplainResponse explain(String topicOrFieldKey, String profileJson) {
		Explanations.Entry reference = Explanations.lookup(topicOrFieldKey);
		String referenceText = reference == null ? "(no canned reference for this topic)" : reference.text();

		Draft draft = chatClient.prompt()
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
				.entity(Draft.class);

		List<String> related = reference == null ? List.of() : reference.related();
		return new ExplainResponse(draft.explanation(), related, draft.followUpQuestion());
	}
}
