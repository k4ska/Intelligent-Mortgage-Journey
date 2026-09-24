package com.seb.mortgage.agent;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Wraps every {@code ChatClient} call: refuses the call once today's token budget is
 * spent, and afterwards reports the response's token usage to {@link TokenUsageTracker}.
 *
 * <p>Registered on every {@code ChatClient.Builder} by a customizer in {@link AgentConfig}.
 * Each agent names itself through the {@link #AGENT} advisor param (set once in
 * {@link LlmSpecialistAgent}), so the log shows which agent spent the tokens.
 */
class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

	/** Advisor-context key holding the calling agent's name. */
	static final String AGENT = "tokenUsage.agent";

	private final TokenUsageTracker tracker;

	TokenUsageAdvisor(TokenUsageTracker tracker) {
		this.tracker = tracker;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		if (tracker.budgetExceeded()) {
			throw new TokenBudgetExceededException();
		}
		ChatClientResponse response = chain.nextCall(request);
		record(request, response.chatResponse());
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		if (tracker.budgetExceeded()) {
			return Flux.error(new TokenBudgetExceededException());
		}
		// Usage arrives on the last chunk(s) of a stream; keep the most complete one seen.
		AtomicReference<ChatResponse> best = new AtomicReference<>();
		return chain.nextStream(request)
				.doOnNext(chunk -> {
					ChatResponse r = chunk.chatResponse();
					if (totalOf(r) > 0 && totalOf(r) >= totalOf(best.get())) {
						best.set(r);
					}
				})
				// doFinally rather than doOnComplete: a client that disconnects mid-answer has
				// still been billed for the tokens streamed so far.
				.doFinally(signal -> {
					if (best.get() != null || signal == SignalType.ON_COMPLETE) {
						record(request, best.get());
					}
				});
	}

	private void record(ChatClientRequest request, ChatResponse response) {
		String agent = String.valueOf(request.context().getOrDefault(AGENT, "unknown"));
		Usage usage = response == null ? null : response.getMetadata().getUsage();
		String model = response == null ? null : response.getMetadata().getModel();
		tracker.record(agent, model, tokens(usage == null ? null : usage.getPromptTokens()),
				tokens(usage == null ? null : usage.getCompletionTokens()));
	}

	private static long totalOf(ChatResponse r) {
		if (r == null || r.getMetadata() == null || r.getMetadata().getUsage() == null) {
			return 0;
		}
		return tokens(r.getMetadata().getUsage().getTotalTokens());
	}

	private static long tokens(Integer n) {
		return n == null ? 0 : n;
	}

	@Override
	public String getName() {
		return "TokenUsageAdvisor";
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
