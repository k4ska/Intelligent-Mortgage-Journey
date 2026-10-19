package com.seb.mortgage.advisor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.seb.mortgage.agent.TokenBudgetExceededException;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs one advisor turn: saves the user's message, streams the advisor's reply with the
 * events its tools push in between, then saves the reply. The orchestrator works for the
 * user only; it never talks to the bank side (see {@code docs/architecture.md} §1).
 */
@Service
public class AdvisorOrchestrator {

	static final String OFFLINE_REPLY = "The assistant is offline. Please add an API key.";

	/** How much of the conversation the advisor sees each turn, to keep prompts bounded. */
	private static final int HISTORY_LIMIT = 40;

	private static final Logger log = LoggerFactory.getLogger(AdvisorOrchestrator.class);

	private final ObjectProvider<AdvisorAgent> advisor;
	private final ConversationService conversations;
	private final ProfileService profile;
	private final PropertyService property;
	private final ListingReader listings;
	private final BankCatalogService banks;
	private final ObjectMapper objectMapper;

	public AdvisorOrchestrator(ObjectProvider<AdvisorAgent> advisor, ConversationService conversations,
			ProfileService profile, PropertyService property, ListingReader listings, BankCatalogService banks,
			ObjectMapper objectMapper) {
		this.advisor = advisor;
		this.conversations = conversations;
		this.profile = profile;
		this.property = property;
		this.listings = listings;
		this.banks = banks;
		this.objectMapper = objectMapper;
	}

	public Flux<StreamEvent> send(UUID userId, String userText) {
		List<Message> earlier = conversations.history(userId);
		conversations.append(userId, Message.Role.USER, userText);

		// Tools push their events here while the reply is still streaming.
		Sinks.Many<StreamEvent> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
		AdvisorTools tools = new AdvisorTools(userId, profile, property, listings, banks,
				event -> toolEvents.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1))));

		StringBuilder reply = new StringBuilder();
		Flux<StreamEvent> text = replyText(userId, earlier, userText, tools)
				.doOnNext(reply::append)
				.map(StreamEvent::text)
				.doFinally(signal -> toolEvents.tryEmitComplete());

		return Flux.merge(text, toolEvents.asFlux())
				.concatWith(Mono.fromCallable(() -> StreamEvent.done(
						conversations.append(userId, Message.Role.ADVISOR, reply.toString()).getId())))
				.onErrorResume(e -> {
					log.warn("Advisor turn failed", e);
					return Flux.just(StreamEvent.error("Something went wrong. Please try again."));
				});
	}

	private Flux<String> replyText(UUID userId, List<Message> earlier, String userText, AdvisorTools tools) {
		AdvisorAgent agent = advisor.getIfAvailable();
		if (agent == null) {
			return Flux.just(OFFLINE_REPLY);
		}
		return Flux.defer(() -> agent.reply(toPrompt(earlier), userText, profileJson(userId), tools))
				.onErrorResume(TokenBudgetExceededException.class, e -> Flux.just(OFFLINE_REPLY));
	}

	private static List<org.springframework.ai.chat.messages.Message> toPrompt(List<Message> earlier) {
		return earlier.stream()
				.skip(Math.max(0, earlier.size() - HISTORY_LIMIT))
				.<org.springframework.ai.chat.messages.Message>map(m -> m.getRole() == Message.Role.USER
						? new UserMessage(m.getText())
						: new AssistantMessage(m.getText()))
				.toList();
	}

	private String profileJson(UUID userId) {
		Map<String, Object> known = new LinkedHashMap<>();
		known.put("fields", profile.fields(userId));
		known.put("property", property.active(userId).orElse(null));
		return objectMapper.writeValueAsString(known);
	}
}
