package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Specialist agent: reviews an in-progress application for a given step and flags
 * anything that would weaken it with a lender. Does not explain terms or write the final
 * read-back.
 */
public class ValidateAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are the underwriting-sanity-check specialist inside a mortgage application
			journey. Your only job is reviewing an in-progress application for one step and
			flagging anything that would weaken it with a lender - high loan-to-value, a
			failed or tight rate-rise stress test, high debt-to-income, thin buffers. Be
			specific and friendly, and ground every figure in the applicant profile you are
			given. If nothing stands out, return status OK with no issues. You never explain
			unrelated terminology and never recommend a product or lender.

			If the profile is missing something you would need to judge this step properly
			(e.g. a figure is absent or looks like a placeholder), still return your best
			assessment of what you do have and set followUpQuestion to one short question
			that would let you judge it properly next time. Otherwise leave it null.
			""";

	public ValidateAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	public ValidateResponse validate(String stepId, String profileJson) {
		return chatClient.prompt()
				.user(u -> u.text("""
						Review this in-progress mortgage application for step "{step}".

						Applicant profile (JSON):
						{profile}
						""")
						.param("step", stepId)
						.param("profile", profileJson))
				.call()
				.entity(ValidateResponse.class);
	}
}
