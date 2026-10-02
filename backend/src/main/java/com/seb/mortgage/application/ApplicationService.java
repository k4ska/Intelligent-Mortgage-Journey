package com.seb.mortgage.application;

import com.seb.mortgage.flow.FlowDefinition;
import com.seb.mortgage.flow.FlowService;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileFacts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

	private final LoanApplicationRepository repository;
	private final FlowService flowService;
	private final LoanProfileAssembler profileAssembler;

	public ApplicationService(LoanApplicationRepository repository,
			FlowService flowService,
			LoanProfileAssembler profileAssembler) {
		this.repository = repository;
		this.flowService = flowService;
		this.profileAssembler = profileAssembler;
	}

	@Transactional
	public LoanApplication create() {
		return repository.save(new LoanApplication(flowService.firstStep().id()));
	}

	@Transactional(readOnly = true)
	public LoanApplication get(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("No application " + id));
	}

	/**
	 * Merge the answers for {@code stepId}. With {@code advance}, mark the step done and move
	 * {@code currentStepId} to whatever the flow rules say comes next; without it, just save
	 * and stay on {@code stepId} (used by "Check this step" and before navigating away).
	 */
	@Transactional
	public LoanApplication submitStep(UUID id, String stepId, Map<String, Object> answers, boolean advance) {
		LoanApplication application = get(id);
		application.putAnswers(answers);
		application.setCurrentStepId(stepId);
		if (advance) {
			application.markCompleted(stepId);
			flowService.nextStep(stepId, application.getAnswers())
					.ifPresent(next -> application.setCurrentStepId(next.id()));
		}
		syncWithPath(application);
		return repository.save(application);
	}

	/**
	 * Move the user to {@code stepId}, e.g. back to an earlier step. Only steps in
	 * {@link Journey#unlockedSteps} are allowed, so nobody can skip ahead past unanswered steps.
	 */
	@Transactional
	public LoanApplication goToStep(UUID id, String stepId) {
		LoanApplication application = get(id);
		if (!journey(application).unlockedSteps().contains(stepId)) {
			throw new IllegalStateException("Step " + stepId + " is not available yet");
		}
		application.setCurrentStepId(stepId);
		return repository.save(application);
	}

	/**
	 * Where the user is in the flow.
	 *
	 * @param path           the route through the flow for the current answers, first to last
	 * @param completedSteps steps on the path the user has continued past
	 * @param unlockedSteps  steps the user may open: every step up to and including the first
	 *                       one not completed yet
	 */
	public record Journey(List<String> path, List<String> completedSteps, List<String> unlockedSteps) {}

	public Journey journey(LoanApplication application) {
		List<String> path = flowService.path(application.getAnswers()).stream()
				.map(FlowDefinition.Step::id).toList();
		List<String> completed = path.stream().filter(application.getCompletedSteps()::contains).toList();
		List<String> unlocked = new ArrayList<>();
		for (String stepId : path) {
			unlocked.add(stepId);
			if (!completed.contains(stepId)) {
				break;
			}
		}
		return new Journey(path, completed, unlocked);
	}

	/**
	 * After answers change, a branching answer may have taken steps off the path (e.g. "no
	 * co-applicant" after all). Their answers are dropped so they don't feed the profile or
	 * the agents, and the application is complete only while every step on the path is.
	 */
	private void syncWithPath(LoanApplication application) {
		List<FlowDefinition.Step> path = flowService.path(application.getAnswers());
		Set<String> pathIds = path.stream().map(FlowDefinition.Step::id).collect(Collectors.toSet());
		List<String> offPathKeys = flowService.definition().steps().stream()
				.filter(step -> !pathIds.contains(step.id()))
				.flatMap(step -> step.fields().stream().map(FlowDefinition.Field::key))
				.toList();
		application.removeAnswers(offPathKeys);
		application.retainCompleted(pathIds);
		application.setComplete(pathIds.stream().allMatch(application.getCompletedSteps()::contains));
	}

	@Transactional(readOnly = true)
	public LoanProfile profile(UUID id) {
		return profileAssembler.assemble(get(id));
	}

	/** What the agents may know: real answers only, no defaults. See {@link ProfileFacts}. */
	@Transactional(readOnly = true)
	public ProfileFacts facts(UUID id) {
		return profileAssembler.facts(get(id));
	}

	/** {@link #facts} narrowed to one step, for checking that step's answers. */
	@Transactional(readOnly = true)
	public ProfileFacts.StepFacts stepFacts(UUID id, String stepId) {
		Optional<FlowDefinition.Step> step = flowService.step(stepId);
		List<String> keys = step.map(s -> s.fields().stream().map(FlowDefinition.Field::key).toList())
				.orElse(List.of());
		String title = step.map(FlowDefinition.Step::title).orElse(stepId);
		return facts(id).forStep(stepId, title, keys);
	}
}
