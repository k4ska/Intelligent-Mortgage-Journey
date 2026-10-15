package com.seb.mortgage.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

	private final AgentMode agentMode;
	private final TokenUsageTracker tokenUsage;

	public AgentController(AgentMode agentMode, TokenUsageTracker tokenUsage) {
		this.agentMode = agentMode;
		this.tokenUsage = tokenUsage;
	}

	/** Today's LLM token usage against the daily budget, per agent. In-memory only. */
	@GetMapping("/usage")
	public TokenUsageTracker.UsageSnapshot usage() {
		return tokenUsage.snapshot();
	}

	/** Tells the frontend whether the real agents or the stub are active. */
	@GetMapping("/status")
	public Map<String, String> status() {
		return Map.of("mode", agentMode.value());
	}
}
