package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import reactor.core.publisher.Flux;

/** Only the last round of a turn reaches the user (#88). */
class LastRoundTextTest {

	private static ChatResponse chunk(String roundId, String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
				ChatResponseMetadata.builder().id(roundId).build());
	}

	@Test
	void textFromRoundsThatEndedInAToolCallIsDropped() {
		Flux<ChatResponse> turn = Flux.just(
				chunk("round-1", "Thanks, I've saved the property. "),
				chunk("round-1", "How much do you earn?"),
				chunk("round-2", "Thanks, I've saved your savings as €0."),
				chunk("round-3", "Thanks, I've saved the property. "),
				chunk("round-3", "How much do you earn per month after tax?"),
				chunk("round-3", ""));

		assertThat(AdvisorAgent.lastRoundText(turn).collectList().block())
				.containsExactly("Thanks, I've saved the property. How much do you earn per month after tax?");
	}

	@Test
	void aTurnWithoutToolsIsKeptWhole() {
		Flux<ChatResponse> turn = Flux.just(chunk("only", "Hello"), chunk("only", " there"));

		assertThat(AdvisorAgent.lastRoundText(turn).collectList().block()).containsExactly("Hello there");
	}

	@Test
	void chunksWithoutAnIdCountAsTheSameRound() {
		Flux<ChatResponse> turn = Flux.just(
				new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" there")))));

		assertThat(AdvisorAgent.lastRoundText(turn).collectList().block()).containsExactly("Hello there");
	}

	@Test
	void noTextMeansNoReplyChunk() {
		assertThat(AdvisorAgent.lastRoundText(Flux.just(chunk("r", ""))).collectList().block()).isEmpty();
	}
}
