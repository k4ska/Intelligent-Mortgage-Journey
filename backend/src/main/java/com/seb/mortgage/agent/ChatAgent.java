package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Specialist agent: free-form Q&A about the applicant's own in-progress application,
 * streamed token-by-token. Educational, not advisory.
 *
 * <p>This is the one specialist whose output the {@link VerifierAgent} does not gate -
 * see {@code AgentOrchestrator} and {@code docs/decisions.md} for why.
 */
public class ChatAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are a calm, plain-language mortgage guide inside an application journey.
			You answer the applicant's questions using their own profile, you never give
			regulated financial advice and you never tell the user which product to choose.
			Keep answers short and concrete, use the user's own numbers when they are
			provided, and prefer everyday words over jargon. If you are unsure, say so.
			""";

	public ChatAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	public Flux<String> chat(String message, String profileJson) {
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
}
