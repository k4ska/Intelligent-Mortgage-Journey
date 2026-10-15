package com.seb.mortgage.profile;

import java.util.List;
import java.util.Map;

/**
 * What the agents are allowed to know about a user: only what the user actually
 * entered, plus the figures that can be worked out from those entries.
 *
 * <p>{@link LoanProfile} fills every missing answer with a default (e.g. a 250 000
 * purchase price) so the summary and bank offers always have something to show. Those
 * defaults must never reach an agent, or it comments on numbers the user never gave. A
 * {@link Figure} is therefore only included once every input it depends on is real.
 *
 * @param answers        the user's answers, without blank or unanswered fields
 * @param notAnsweredYet inputs the user has not given yet; nothing may be said about their value
 * @param figures        derived figures whose inputs are all real
 * @param documents      documents a lender would ask for, given the answers so far
 */
public record ProfileFacts(
		Map<String, Object> answers,
		List<String> notAnsweredYet,
		List<Figure> figures,
		List<String> documents
) {

	/**
	 * @param basedOn the answer keys this figure is worked out from
	 * @param concern what a lender would make of it, or null if nothing stands out
	 */
	public record Figure(String name, Object value, List<String> basedOn, String concern) {}
}
