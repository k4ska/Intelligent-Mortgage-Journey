package com.seb.mortgage.application;

import com.seb.mortgage.mockdata.BankCatalogService;
import com.seb.mortgage.profile.LoanProfile;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

	private final ApplicationService applicationService;
	private final BankCatalogService bankCatalogService;

	public ApplicationController(ApplicationService applicationService,
			BankCatalogService bankCatalogService) {
		this.applicationService = applicationService;
		this.bankCatalogService = bankCatalogService;
	}

	@PostMapping
	public ResponseEntity<ApplicationView> create() {
		LoanApplication created = applicationService.create();
		return ResponseEntity
				.created(URI.create("/api/applications/" + created.getId()))
				.body(view(created));
	}

	@GetMapping("/{id}")
	public ApplicationView get(@PathVariable UUID id) {
		return view(applicationService.get(id));
	}

	@PostMapping("/{id}/steps")
	public ApplicationView submitStep(@PathVariable UUID id, @RequestBody SubmitStepRequest request) {
		boolean advance = request.advance() == null || request.advance();
		return view(applicationService.submitStep(id, request.stepId(), request.answers(), advance));
	}

	/** Jump to a step the user has already reached, e.g. back to fix an earlier answer. */
	@PutMapping("/{id}/current-step")
	public ApplicationView goToStep(@PathVariable UUID id, @RequestBody GoToStepRequest request) {
		return view(applicationService.goToStep(id, request.stepId()));
	}

	@GetMapping("/{id}/profile")
	public LoanProfile profile(@PathVariable UUID id) {
		return applicationService.profile(id);
	}

	/** Mock multi-bank comparison: the standardized profile fanned out to sample offers. */
	@GetMapping("/{id}/offers")
	public List<BankCatalogService.Offer> offers(@PathVariable UUID id) {
		return bankCatalogService.generateOffers(applicationService.profile(id));
	}

	/** {@code advance} defaults to true; false saves the answers without moving on. */
	public record SubmitStepRequest(@NotBlank String stepId, Map<String, Object> answers, Boolean advance) {}

	public record GoToStepRequest(@NotBlank String stepId) {}

	private ApplicationView view(LoanApplication application) {
		return ApplicationView.of(application, applicationService.journey(application));
	}

	public record ApplicationView(
			UUID id,
			String currentStepId,
			boolean complete,
			Map<String, Object> answers,
			Instant createdAt,
			Instant updatedAt,
			/** Step ids on the route through the flow for the current answers, first to last. */
			List<String> path,
			List<String> completedSteps,
			/** Steps the user may jump to. */
			List<String> unlockedSteps
	) {
		static ApplicationView of(LoanApplication a, ApplicationService.Journey journey) {
			return new ApplicationView(a.getId(), a.getCurrentStepId(), a.isComplete(),
					a.getAnswers(), a.getCreatedAt(), a.getUpdatedAt(),
					journey.path(), journey.completedSteps(), journey.unlockedSteps());
		}
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	@org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
	public Map<String, String> handleLocked(IllegalStateException e) {
		return Map.of("error", e.getMessage());
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@org.springframework.web.bind.annotation.ExceptionHandler(java.util.NoSuchElementException.class)
	public Map<String, String> handleNotFound(java.util.NoSuchElementException e) {
		return Map.of("error", e.getMessage());
	}
}
