package com.seb.mortgage.advisor;

import com.seb.mortgage.auth.CurrentUser;
import com.seb.mortgage.conversation.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	public record MessageView(Long id, String role, String text, Instant createdAt) {}

	/** Either what the user typed, or a trigger for something that happened outside the chat. */
	public record ChatRequest(String text, String trigger, UUID documentId) {}

	private final ConversationService conversations;
	private final AdvisorOrchestrator orchestrator;
	private final StartOverService startOver;
	private final CurrentUser currentUser;

	public ChatController(ConversationService conversations, AdvisorOrchestrator orchestrator,
			StartOverService startOver, CurrentUser currentUser) {
		this.conversations = conversations;
		this.orchestrator = orchestrator;
		this.startOver = startOver;
		this.currentUser = currentUser;
	}

	/** The conversation so far; a new user's starts with the advisor's greeting. */
	@GetMapping
	public List<MessageView> history() {
		return conversations.history(currentUser.id()).stream()
				.map(m -> new MessageView(m.getId(), m.getRole().name().toLowerCase(Locale.ROOT),
						m.getText(), m.getCreatedAt()))
				.toList();
	}

	/**
	 * Start over (#70): wipes everything the user has told us and sent, keeps the account,
	 * and returns the new conversation, which is just the greeting.
	 */
	@PostMapping("/reset")
	public List<MessageView> reset() {
		startOver.wipe(currentUser.id());
		return history();
	}

	/** One advisor turn, streamed as server-sent events (see {@link StreamEvent}). */
	@PostMapping(value = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<Map<String, Object>>> send(@RequestBody ChatRequest request) {
		if (request != null && request.trigger() != null) {
			return toSse(trigger(request));
		}
		if (request == null || request.text() == null || request.text().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
		}
		return toSse(orchestrator.send(currentUser.id(), request.text().trim()));
	}

	private Flux<StreamEvent> trigger(ChatRequest request) {
		if (!"DOCUMENT_UPLOADED".equals(request.trigger())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown trigger: " + request.trigger());
		}
		if (request.documentId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId is required");
		}
		try {
			return orchestrator.documentUploaded(currentUser.id(), request.documentId());
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such document");
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
		}
	}

	private static Flux<ServerSentEvent<Map<String, Object>>> toSse(Flux<StreamEvent> events) {
		return events.map(event -> ServerSentEvent.builder(event.data()).event(event.type()).build());
	}
}
