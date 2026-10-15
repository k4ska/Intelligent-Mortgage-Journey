package com.seb.mortgage.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Flux;

/**
 * Runs the real verifier against a fake ChatModel that reports 100 input + 20 output
 * tokens per call, with a 120-token budget: exactly one call fits.
 */
@SpringBootTest(properties = {
		"spring.ai.openai.api-key=sk-dummy",
		"app.token-budget.daily-limit=120" })
@Import(TokenUsageIntegrationTest.FakeModelConfig.class)
class TokenUsageIntegrationTest {

	@TestConfiguration
	static class FakeModelConfig {

		@Bean
		@Primary
		@SuppressWarnings("unused")
		FakeChatModel fakeChatModel() {
			return new FakeChatModel();
		}
	}

	static class FakeChatModel implements ChatModel {
		final AtomicInteger calls = new AtomicInteger();

		@Override
		public ChatResponse call(Prompt prompt) {
			calls.incrementAndGet();
			return response("{\"approved\": true, \"rewrite\": null}");
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			calls.incrementAndGet();
			return Flux.just(response("Hello"));
		}

		private static ChatResponse response(String text) {
			return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
					ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(100, 20)).build());
		}
	}

	@Autowired VerifierAgent verifier;
	@Autowired TokenUsageTracker tracker;
	@Autowired FakeChatModel model;

	@Test
	void countsTokensPerAgentThenStopsCallingTheModelWhenBudgetIsSpent() {
		var first = verifier.verify("task", "A draft.", "{}");
		assertThat(first.approved()).isTrue();
		assertThat(first.note()).isNull();

		var usage = tracker.snapshot();
		assertThat(usage.usedTokens()).isEqualTo(120);
		assertThat(usage.byAgent()).containsOnlyKeys("VerifierAgent");
		assertThat(usage.byAgent().get("VerifierAgent").inputTokens()).isEqualTo(100);
		assertThat(usage.budgetExceeded()).isTrue();

		// Budget spent: TokenUsageAdvisor refuses the call, so the model is not called again, and
		// the verifier fails open with the draft unchanged.
		int callsBefore = model.calls.get();
		var second = verifier.verify("task", "A draft.", "{}");
		assertThat(second.output()).isEqualTo("A draft.");
		assertThat(second.note()).contains("Daily token budget reached");
		assertThat(model.calls.get()).isEqualTo(callsBefore);
	}
}
