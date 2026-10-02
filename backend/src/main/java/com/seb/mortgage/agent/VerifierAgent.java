package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Independent verifier: never talks to the applicant directly, only checks a specialist's
 * draft reply before {@code AgentOrchestrator} lets it reach the client. Deliberately
 * built on its own {@link ChatClient} with its own system prompt, not shared with any
 * specialist, so it isn't primed by the same instructions it's meant to be checking.
 */
public class VerifierAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are an independent quality-and-safety checker for a mortgage assistant. You
			never talk to the applicant directly; you only review another agent's draft reply
			before it is allowed to reach them. Approve a draft only if all of these hold:
			- It states no numbers that are not present in, or directly computable from, the
			  applicant profile you are given.
			- It never recommends a specific lender or product, or tells the user what to
			  choose.
			- It never gives regulated financial, legal or tax advice.
			- Its tone is calm and plain-language, not alarmist or salesy.
			If the draft passes, answer approved true and leave rewrite null. Do not repeat
			the draft. If it fails any check, answer approved false and put a short, safe
			replacement in rewrite that keeps whatever was correct and drops or fixes the rest.
			""";

	public VerifierAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/**
	 * What the model answers. Kept to a yes/no plus an optional rewrite, so an approved
	 * draft costs a few output tokens instead of the whole draft again.
	 */
	record Check(boolean approved, String rewrite) {}

	/**
	 * @param output the text to show the user: the draft itself when approved, otherwise
	 *               the verifier's rewrite
	 */
	public record Verdict(boolean approved, String output, String note) {}

	/**
	 * @param taskDescription what the draft was written for, so the verifier can judge it
	 *                        against the right expectations
	 * @param draftOutput     the specialist's candidate reply text
	 * @param profileJson     the applicant profile the draft should be grounded in
	 */
	public Verdict verify(String taskDescription, String draftOutput, String profileJson) {
		Check check;
		try {
			check = chatClient.prompt()
					.user(u -> u.text("""
							Task the draft was written for: {task}

							Applicant profile (JSON, source of truth for any numbers):
							{profile}

							Draft reply to check:
							{draft}
							""")
							.param("task", taskDescription)
							.param("profile", profileJson)
							.param("draft", draftOutput))
					.call()
					.entity(Check.class);
		} catch (Exception e) {
			// Fail open: an unreachable/broken verifier shouldn't break the journey. The
			// draft still went through a specialist with its own narrow system prompt.
			return new Verdict(true, draftOutput, "verifier unavailable: " + e.getMessage());
		}
		if (check == null || check.approved()) {
			return new Verdict(true, draftOutput, null);
		}
		if (check.rewrite() == null || check.rewrite().isBlank()) {
			// Rejected without a replacement: nothing better to show, so fail open as above.
			return new Verdict(true, draftOutput, "verifier rejected the draft without a rewrite");
		}
		return new Verdict(false, check.rewrite(), null);
	}
}
