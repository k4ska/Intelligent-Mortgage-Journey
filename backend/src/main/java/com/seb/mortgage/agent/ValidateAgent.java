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
			journey. The applicant has just filled in one step and asked you to check it. Judge
			only that step: its answers (stepAnswers), any of its fields still left blank
			(stepUnanswered), and the figures those answers feed into (relevantFigures). Use
			otherAnswers only as context.

			Flag what would weaken the application with a lender - a figure whose concern is
			set, a blank field on this step, or an answer that looks implausible. Be specific
			and friendly. Only ever quote numbers that appear in the facts you are given. Never
			guess or assume a value for anything in notAnsweredYet, and do not comment on
			loan-to-value, payments, debt-to-income or the stress test unless that figure is in
			relevantFigures - the applicant simply hasn't reached those questions yet. If nothing
			on this step stands out, return status OK with no issues. You never explain unrelated
			terminology and never recommend a product or lender.

			If this step's answers leave something you would need unclear, set followUpQuestion
			to one short question about it. Otherwise leave it null.
			""";

	public ValidateAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/** @param stepFactsJson the step's facts, see {@code ProfileFacts.StepFacts} */
	public ValidateResponse validate(String stepFactsJson) {
		return chatClient.prompt()
				.user(u -> u.text("""
						Check the applicant's answers for this step of their mortgage application.

						Step facts (JSON):
						{facts}
						""")
						.param("facts", stepFactsJson))
				.call()
				.entity(ValidateResponse.class);
	}
}
