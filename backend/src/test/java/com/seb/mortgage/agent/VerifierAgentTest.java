package com.seb.mortgage.agent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * The verifier only answers yes/no plus an optional rewrite; it should hand back the
 * specialist's own draft unless it actually rewrote it.
 */
class VerifierAgentTest {

	private static final String DRAFT = "Your loan-to-value is 85%.";

	private static VerifierAgent verifierAnswering(String json) {
		ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
		return new VerifierAgent(ChatClient.builder(model));
	}

	@Test
	void approvedDraftIsReturnedAsIs() {
		var verdict = verifierAnswering("{\"approved\": true, \"rewrite\": null}").verify("task", DRAFT, "{}");

		assertThat(verdict.approved()).isTrue();
		assertThat(verdict.output()).isEqualTo(DRAFT);
	}

	@Test
	void rejectedDraftIsReplacedByTheRewrite() {
		var verdict = verifierAnswering("{\"approved\": false, \"rewrite\": \"Safer wording.\"}")
				.verify("task", DRAFT, "{}");

		assertThat(verdict.approved()).isFalse();
		assertThat(verdict.output()).isEqualTo("Safer wording.");
	}

	@Test
	void rejectionWithoutRewriteFailsOpen() {
		var verdict = verifierAnswering("{\"approved\": false, \"rewrite\": \"\"}").verify("task", DRAFT, "{}");

		assertThat(verdict.approved()).isTrue();
		assertThat(verdict.output()).isEqualTo(DRAFT);
		assertThat(verdict.note()).isNotBlank();
	}

	@Test
	void brokenVerifierFailsOpen() {
		var verdict = verifierAnswering("not json").verify("task", DRAFT, "{}");

		assertThat(verdict.approved()).isTrue();
		assertThat(verdict.output()).isEqualTo(DRAFT);
	}
}
