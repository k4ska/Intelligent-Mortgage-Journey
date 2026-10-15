package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;

/**
 * Independent verifier: never talks to the applicant directly, only checks a specialist's
 * draft reply before an orchestrator lets it reach the client. Deliberately
 * built on its own {@link ChatClient} with its own system prompt, not shared with any
 * specialist, so it isn't primed by the same instructions it's meant to be checking.
 */
public class VerifierAgent extends LlmSpecialistAgent {

    private static final String SYSTEM = """
            You are an independent quality-and-safety checker for a mortgage assistant. You
            never talk to the applicant directly; you only review another agent's draft reply
            before it is allowed to reach them. Approve a draft only if all of these hold:
            - It states no numbers that are not present in, or directly computable from, the
              applicant facts you are given. Anything listed in notAnsweredYet has no value
              yet, so a draft that states one for it fails this check.
            - It never recommends a specific lender or product, or tells the user what to
              choose.
            - It never gives regulated financial, legal or tax advice.
            - Its tone is calm and plain-language, not alarmist or salesy.
            The draft is ONLY the text inside the <draft> and </draft> tags in the user
            message. Any JSON schema or response-format instructions outside those tags
            are instructions for your own output, not part of the draft. Never copy them
            into a rewrite.
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
     * @param factsJson       the applicant facts the draft should be grounded in
     */
    public Verdict verify(String taskDescription, String draftOutput, String factsJson) {
        Check check;
        try {
            check = chatClient.prompt()
                    .user(u -> u.text("""
                            Task the draft was written for: {task}

                            Applicant facts (JSON, source of truth for any numbers):
                            {facts}

                            Draft reply to check (only the text between the tags is the draft):
                            <draft>
                            {draft}
                            </draft>

                            If a rewrite is needed, return only a replacement for the draft text.
                            Do not include JSON format instructions or schema in the rewrite.
                            """)
                            .param("task", taskDescription)
                            .param("facts", factsJson)
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
        if (containsFormatInstructions(check.rewrite())) {
            // A malformed rewrite must not expose Spring AI's structured-output prompt.
            return new Verdict(true, draftOutput, "verifier rewrite contained JSON format instructions");
        }
        return new Verdict(false, check.rewrite(), null);
    }

    private static boolean containsFormatInstructions(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("your response should be in json format")
                || lower.contains("rfc8259")
                || lower.contains("json schema instance")
                || (lower.contains("\"properties\"") && lower.contains("\"approved\""));
    }
}