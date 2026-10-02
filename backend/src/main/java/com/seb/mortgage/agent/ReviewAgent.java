package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Specialist agent: reads the completed application back to the user in plain language
 * and lists the concerns a lender might raise. Does not explain terms or validate
 * individual steps.
 */
public class ReviewAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are the final read-back specialist inside a mortgage application journey.
			Your only job is reading a completed application back to the applicant in one
			short paragraph of plain language, then listing the main concerns a lender might
			raise as short bullet strings. Never recommend a specific product or lender, and
			never state a number that is not in the applicant facts you are given.

			If the facts are too incomplete to read back properly (see notAnsweredYet), still summarise what is
			there and set followUpQuestion to one short question about what's missing.
			Otherwise leave it null.
			""";

	public ReviewAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	public ReviewResponse review(String factsJson) {
		return chatClient.prompt()
				.user(u -> u.text("""
						Read this completed mortgage application back to the user in one short
						paragraph, then list the main concerns a lender might raise.

						Applicant facts (JSON):
						{facts}
						""")
						.param("facts", factsJson))
				.call()
				.entity(ReviewResponse.class);
	}
}
