package com.seb.mortgage.agent;

/**
 * Whether the real agents are wired up ({@code "openai"}) or the app runs without an API
 * key ({@code "stub"}). Reported by {@code GET /api/agent/status}.
 */
public record AgentMode(String value) {}
