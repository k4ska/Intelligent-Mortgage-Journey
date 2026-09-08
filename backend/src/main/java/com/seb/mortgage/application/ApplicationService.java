package com.seb.mortgage.application;

import com.seb.mortgage.flow.FlowDefinition;
import com.seb.mortgage.flow.FlowService;
import com.seb.mortgage.profile.LoanProfile;
import com.seb.mortgage.profile.LoanProfileAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

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
	 * Merge the answers for {@code stepId}, then advance {@code currentStepId} to whatever
	 * the flow rules say comes next. Marks the application complete when there is no next step.
	 */
	@Transactional
	public LoanApplication submitStep(UUID id, String stepId, Map<String, Object> answers) {
		LoanApplication application = get(id);
		application.putAnswers(answers);

		Optional<FlowDefinition.Step> next = flowService.nextStep(stepId, application.getAnswers());
		if (next.isPresent()) {
			application.setCurrentStepId(next.get().id());
			application.setComplete(false);
		} else {
			application.setComplete(true);
		}
		return repository.save(application);
	}

	@Transactional(readOnly = true)
	public LoanProfile profile(UUID id) {
		return profileAssembler.assemble(get(id));
	}
}
