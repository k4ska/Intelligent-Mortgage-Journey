package com.seb.mortgage.flow;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the flow definition from {@code classpath:flow/flow-definition.json} and answers
 * "given the answers so far, what is the next step?". This is where the adaptive flow lives.
 */
@Service
public class FlowService {

	private final FlowDefinition definition;

	public FlowService(ObjectMapper objectMapper,
			org.springframework.core.io.ResourceLoader resourceLoader) {
		Resource resource = resourceLoader.getResource("classpath:flow/flow-definition.json");
		try {
			this.definition = objectMapper.readValue(resource.getInputStream(), FlowDefinition.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not load flow-definition.json", e);
		}
	}

	public FlowDefinition definition() {
		return definition;
	}

	public Optional<FlowDefinition.Step> step(String stepId) {
		return definition.steps().stream().filter(s -> s.id().equals(stepId)).findFirst();
	}

	public FlowDefinition.Step firstStep() {
		return definition.steps().get(0);
	}

	/**
	 * The route through the flow for these answers, from the first step to the last.
	 * Branches whose condition isn't met (yet) are left out, so the path changes as the
	 * user answers branching questions.
	 */
	public List<FlowDefinition.Step> path(Map<String, Object> answers) {
		List<FlowDefinition.Step> path = new ArrayList<>();
		Optional<FlowDefinition.Step> step = Optional.of(firstStep());
		// Bounded by the number of steps, in case the definition ever routes in a loop.
		while (step.isPresent() && path.size() < definition.steps().size() && !path.contains(step.get())) {
			path.add(step.get());
			step = nextStep(step.get().id(), answers);
		}
		return path;
	}

	/**
	 * Resolves the step that follows {@code currentStepId} given the collected {@code answers}.
	 * Returns empty when the current step is the last one (journey complete).
	 */
	public Optional<FlowDefinition.Step> nextStep(String currentStepId, Map<String, Object> answers) {
		FlowDefinition.Step current = step(currentStepId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown step: " + currentStepId));

		List<FlowDefinition.NextRule> rules = current.next();
		if (rules != null) {
			for (FlowDefinition.NextRule rule : rules) {
				if (rule.condition() != null && rule.condition().matches(answers)) {
					return step(rule.target());
				}
			}
			for (FlowDefinition.NextRule rule : rules) {
				if (rule.defaultTarget() != null) {
					return step(rule.defaultTarget());
				}
			}
		}

		// No explicit routing: fall through to the next step in document order.
		List<FlowDefinition.Step> steps = definition.steps();
		int idx = -1;
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(currentStepId)) {
				idx = i;
				break;
			}
		}
		return (idx >= 0 && idx + 1 < steps.size()) ? Optional.of(steps.get(idx + 1)) : Optional.empty();
	}
}
