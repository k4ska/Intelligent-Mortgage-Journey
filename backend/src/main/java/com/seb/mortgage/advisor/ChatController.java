package com.seb.mortgage.advisor;

import com.seb.mortgage.auth.CurrentUser;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	/**
	 * @param threadId the bank thread the message was written next to in the inbox (#80), or null
	 * @param chart     a chart the advisor showed with this reply (#130), or null
	 * @param otherChat asked next to a bank thread in another saved chat (#160): the inbox shows
	 *                  it with its thread, the chat doesn't
	 */
	public record MessageView(Long id, String role, String text, Instant createdAt, boolean corrected,
			UUID threadId, ChartView chart, boolean otherChat) {}

	/** A chart button was pressed (#160): which chart to show. */
	public record ChartRequest(String chartType) {}

	/**
	 * Either what the user typed, or a trigger for something that happened outside the chat,
	 * with the id it is about.
	 */
	public record ChatRequest(String text, String trigger, UUID documentId, UUID applicationId, UUID threadId) {}

	private final ConversationService conversations;
	private final AdvisorOrchestrator orchestrator;
	private final StartOverService startOver;
	private final CurrentUser currentUser;
	private final ObjectMapper objectMapper;
	private final ChartService charts;

	public ChatController(ConversationService conversations, AdvisorOrchestrator orchestrator,
			StartOverService startOver, CurrentUser currentUser, ObjectMapper objectMapper, ChartService charts) {
		this.charts = charts;
		this.conversations = conversations;
		this.orchestrator = orchestrator;
		this.startOver = startOver;
		this.currentUser = currentUser;
		this.objectMapper = objectMapper;
	}

	/** The conversation so far; a new user's starts with the advisor's greeting. */
	@GetMapping
	public List<MessageView> history() {
		List<Message> messages = conversations.history(currentUser.id());
		UUID open = conversations.activeId(currentUser.id());
		return messages.stream().map(m -> view(m, open)).toList();
	}

	/** Saved chats for the current account (#160). */
	@GetMapping("/conversations")
	public List<ConversationService.Summary> conversations() {
		return conversations.list(currentUser.id());
	}

	/** A fresh chat keeps previous chat transcripts; mortgage details remain user-scoped. */
	@PostMapping("/conversations")
	public ConversationService.Summary newConversation() {
		return conversations.create(currentUser.id());
	}

	/** Existing chat endpoints act on the selected conversation afterwards. */
	@PostMapping("/conversations/{id}/select")
	public void selectConversation(@PathVariable UUID id) {
		try {
			conversations.select(currentUser.id(), id);
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
		}
	}

	private MessageView view(Message m, UUID openChat) {
		return new MessageView(m.getId(), m.getRole().name().toLowerCase(Locale.ROOT), m.getText(), m.getCreatedAt(),
				m.isCorrected(), m.getThreadId(), chart(m), !m.getConversationId().equals(openChat));
	}

	/** A saved chart; one saved before there were several kinds (#130) is the rate chart's figures alone. */
	private ChartView chart(Message message) {
		if (message.getChartJson() == null) {
			return null;
		}
		JsonNode saved = objectMapper.readTree(message.getChartJson());
		return saved.has("chartType")
				? new ChartView(saved.path("chartType").asString(), saved.path("title").asString(), saved.path("data"))
				: objectMapper.treeToValue(saved, RateChart.class).view();
	}

	/** The charts the buttons under the chat can show now (#160); none until the loan is known. */
	@GetMapping("/charts")
	public List<String> charts() {
		return charts.available(currentUser.id());
	}

	/**
	 * Shows a chart straight away, without the model (#160): it is saved as an advisor message,
	 * so it stays in the conversation, and the advisor sees it there next turn.
	 */
	@PostMapping("/charts")
	public MessageView showChart(@RequestBody(required = false) ChartRequest request) {
		if (request == null || request.chartType() == null || !ChartService.TYPES.contains(request.chartType())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown chart");
		}
		UUID userId = currentUser.id();
		ChartView chart = charts.chart(userId, request.chartType())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "This chart needs the loan first"));
		Message message = conversations.append(userId, Message.Role.ADVISOR, CHART_INTROS.get(chart.chartType()));
		conversations.attachChart(message.getId(), objectMapper.writeValueAsString(chart));
		message.attachChart(objectMapper.writeValueAsString(chart));
		return view(message, message.getConversationId());
	}

	/** What the advisor says with a chart shown by a button; the figures are in the chart. */
	static final Map<String, String> CHART_INTROS = Map.of(
			RateChart.TYPE, "Here is how your monthly payment would change if Euribor goes up or down.",
			CostBreakdown.TYPE, "Here is what the home costs in all: your down payment, the loan, and the interest over the whole term.");

	/**
	 * Start over (#70): wipes all saved conversations and user journey data, keeps the account,
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
		UUID userId = currentUser.id();
		try {
			return switch (request.trigger()) {
				case "DOCUMENT_UPLOADED" -> orchestrator.documentUploaded(userId, required(request.documentId(), "documentId"));
				case "APPLICATION_SENT" -> orchestrator.applicationSent(userId, required(request.applicationId(), "applicationId"));
				case "OFFER_ACCEPTED" -> orchestrator.offerAccepted(userId, required(request.threadId(), "threadId"));
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown trigger: " + request.trigger());
			};
		} catch (NoSuchElementException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
		}
	}

	private static UUID required(UUID id, String name) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
		}
		return id;
	}

	static Flux<ServerSentEvent<Map<String, Object>>> toSse(Flux<StreamEvent> events) {
		return events.map(event -> ServerSentEvent.builder(event.data()).event(event.type()).build());
	}
}
