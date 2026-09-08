package com.seb.mortgage.agent;

import com.seb.mortgage.agent.AgentDtos.ExplainRequest;
import com.seb.mortgage.agent.AgentDtos.ExplainResponse;
import com.seb.mortgage.agent.AgentDtos.ReviewRequest;
import com.seb.mortgage.agent.AgentDtos.ReviewResponse;
import com.seb.mortgage.agent.AgentDtos.ValidateRequest;
import com.seb.mortgage.agent.AgentDtos.ValidateResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

	private final AgentService agentService;

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
	}

	/** Tells the frontend whether the real agent or the stub is active. */
	@GetMapping("/status")
	public Map<String, String> status() {
		return Map.of("mode", agentService.mode());
	}

	@PostMapping("/explain")
	public ExplainResponse explain(@RequestBody ExplainRequest request) {
		String key = request.fieldKey() != null ? request.fieldKey() : request.topic();
		return agentService.explain(key, parseId(request.applicationId()));
	}

	@PostMapping("/validate")
	public ValidateResponse validate(@RequestBody ValidateRequest request) {
		return agentService.validate(parseId(request.applicationId()), request.stepId());
	}

	@PostMapping("/review")
	public ReviewResponse review(@RequestBody ReviewRequest request) {
		return agentService.review(parseId(request.applicationId()));
	}

	@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chat(@RequestBody AgentDtos.ChatRequest request) {
		return agentService.chat(parseId(request.applicationId()), request.message());
	}

	private static UUID parseId(String raw) {
		try {
			return raw == null ? null : UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
