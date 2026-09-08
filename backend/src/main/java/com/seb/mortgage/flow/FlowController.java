package com.seb.mortgage.flow;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/flow")
public class FlowController {

	private final FlowService flowService;

	public FlowController(FlowService flowService) {
		this.flowService = flowService;
	}

	/** The full flow definition. The frontend renders the journey from this. */
	@GetMapping
	public FlowDefinition definition() {
		return flowService.definition();
	}

	/** Compute the next step from the current step id and the answers collected so far. */
	@PostMapping("/next")
	public ResponseEntity<NextStepResponse> next(@RequestBody NextStepRequest request) {
		return flowService.nextStep(request.currentStepId(), request.answers() == null ? Map.of() : request.answers())
				.map(step -> ResponseEntity.ok(new NextStepResponse(step, false)))
				.orElseGet(() -> ResponseEntity.ok(new NextStepResponse(null, true)));
	}

	public record NextStepRequest(String currentStepId, Map<String, Object> answers) {}

	public record NextStepResponse(FlowDefinition.Step step, boolean complete) {}
}
