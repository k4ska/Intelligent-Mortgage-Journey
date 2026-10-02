package com.seb.mortgage.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the agents are allowed to know about an application: only what the user actually
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

	/**
	 * The facts narrowed to one step of the journey, for checking that step's answers.
	 *
	 * @param stepAnswers     this step's answers the user has filled in
	 * @param stepUnanswered  this step's fields still left blank
	 * @param otherAnswers    answers from the rest of the journey, for context
	 * @param relevantFigures figures this step's answers feed into (and whose inputs are all real)
	 */
	public record StepFacts(
			String stepId,
			String stepTitle,
			Map<String, Object> stepAnswers,
			List<String> stepUnanswered,
			Map<String, Object> otherAnswers,
			List<String> notAnsweredYet,
			List<Figure> relevantFigures
	) {

		public Optional<Figure> figure(String name) {
			return relevantFigures.stream().filter(f -> f.name().equals(name)).findFirst();
		}
	}

	public StepFacts forStep(String stepId, String stepTitle, Collection<String> stepFieldKeys) {
		Map<String, Object> stepAnswers = new LinkedHashMap<>();
		Map<String, Object> otherAnswers = new LinkedHashMap<>(answers);
		for (String key : stepFieldKeys) {
			if (answers.containsKey(key)) {
				stepAnswers.put(key, otherAnswers.remove(key));
			}
		}
		List<String> stepUnanswered = stepFieldKeys.stream().filter(k -> !answers.containsKey(k)).toList();
		List<Figure> relevant = figures.stream()
				.filter(f -> f.basedOn().stream().anyMatch(stepFieldKeys::contains))
				.toList();
		return new StepFacts(stepId, stepTitle, stepAnswers, stepUnanswered, otherAnswers,
				notAnsweredYet, relevant);
	}
}
