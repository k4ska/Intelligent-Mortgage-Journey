package com.seb.mortgage.advisor;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.seb.mortgage.auth.CurrentUser;

import reactor.core.publisher.Flux;

/**
 * The advisor next to a bank's email thread (#80). It lives with the advisor, not in the
 * bank package's inbox controller, because the bank side may never call the advisor
 * (docs/architecture.md §1).
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxAssistController {

	public record AssistRequest(String text) {}

	private final AdvisorOrchestrator orchestrator;
	private final CurrentUser currentUser;

	public InboxAssistController(AdvisorOrchestrator orchestrator, CurrentUser currentUser) {
		this.orchestrator = orchestrator;
		this.currentUser = currentUser;
	}

	/** One advisor turn about this thread, streamed like the chat's (see {@link StreamEvent}). */
	@PostMapping(value = "/{threadId}/assist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<Map<String, Object>>> assist(@PathVariable UUID threadId,
			@RequestBody AssistRequest request) {
		if (request == null || request.text() == null || request.text().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
		}
		try {
			return ChatController.toSse(orchestrator.assist(currentUser.id(), threadId, request.text().trim()));
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such thread");
		}
	}
}
