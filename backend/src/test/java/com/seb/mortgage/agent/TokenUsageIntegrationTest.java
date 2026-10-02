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
 * Runs the real agents against a fake ChatModel that reports 100 input + 20 output tokens
 * per call, with a 240-token budget: exactly one explain (specialist + verifier) fits.
 */
@SpringBootTest(properties = {
		"spring.ai.anthropic.api-key=sk-ant-dummy",
		"app.token-budget.daily-limit=240" })
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
			String text = prompt.getContents().contains("Draft reply to check")
					? "{\"approved\": true, \"rewrite\": null}"
					: "{\"explanation\": \"LTV is the loan divided by the price.\", \"followUpQuestion\": null}";
			return response(text);
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

	@Autowired AgentService agentService;
	@Autowired TokenUsageTracker tracker;
	@Autowired FakeChatModel model;

	@Test
	void countsTokensPerAgentThenFallsBackToStubWhenBudgetIsSpent() {
		var first = agentService.explain("ltv", null);
		assertThat(first.explanation()).isEqualTo("LTV is the loan divided by the price.");

		var usage = tracker.snapshot();
		assertThat(usage.usedTokens()).isEqualTo(240);
		assertThat(usage.byAgent()).containsOnlyKeys("ExplainAgent", "VerifierAgent");
		assertThat(usage.byAgent().get("ExplainAgent").inputTokens()).isEqualTo(100);
		assertThat(usage.budgetExceeded()).isTrue();

		// Budget spent: answers now come from the stub and the model is not called again.
		int callsBefore = model.calls.get();
		var second = agentService.explain("ltv", null);
		assertThat(second.explanation()).isNotEqualTo("LTV is the loan divided by the price.").isNotBlank();
		String chat = String.join("", agentService.chat(null, "hi").collectList().block());
		assertThat(chat).contains("stub assistant");
		assertThat(model.calls.get()).isEqualTo(callsBefore);
	}
}
