package com.seb.mortgage.bank;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;

import com.seb.mortgage.agent.LlmSpecialistAgent;

/**
 * Reads the user's reply to a bank's offer for the {@link Negotiator}: does it ask for a
 * better offer, and does it bring up another bank's offer? It only reads; the numbers come
 * from the {@link OfferCalculator}.
 */
public class NegotiationAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You read a customer's reply to a bank's mortgage offer in a prototype app. Answer these
			questions about it, nothing else:
			- asksForBetterOffer: does it ask the bank to improve the offer (a lower margin,
			  rate or monthly payment, a discount, to match or beat another offer)? A question
			  about the offer, a thank-you or a "no thanks" is not asking.
			- mentionsCompetingOffer: does it say another bank offered a better deal?
			- questions: which of these it asks about, if any: EARLY_REPAYMENT (paying extra or
			  repaying the loan early, or a fee for that), LONGER_TERM (a longer loan term, like
			  30 years), DOCUMENTS (which documents the bank needs), FEES (contract or other fees).""";

	/** The questions a bank answers from its rules (#129). */
	public enum Question { EARLY_REPAYMENT, LONGER_TERM, DOCUMENTS, FEES }

	/** What the reply asks for. */
	public record Reading(boolean asksForBetterOffer, boolean mentionsCompetingOffer, List<Question> questions) {
		public Reading {
			questions = questions == null ? List.of() : List.copyOf(questions);
		}
	}

	public NegotiationAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	Reading read(String bankName, String reply) {
		Reading reading = chatClient.prompt()
				.user(u -> u.text("The reply to {bank}:\n{reply}").param("bank", bankName).param("reply", reply))
				.call()
				.entity(Reading.class);
		if (reading == null) {
			throw new IllegalStateException("The negotiation reader returned nothing");
		}
		return reading;
	}
}
