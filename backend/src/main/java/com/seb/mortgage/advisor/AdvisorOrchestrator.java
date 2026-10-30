package com.seb.mortgage.advisor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.seb.mortgage.agent.TokenBudgetExceededException;
import com.seb.mortgage.agent.VerifierAgent;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.conversation.Message;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.bank.EmailThread;
import com.seb.mortgage.documents.Document;
import com.seb.mortgage.documents.DocumentReader;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentView;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
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

	/** Tools whose turns talk about money, so the verifier always checks them (#67). */
	static final Set<String> MONEY_TOOLS = Set.of("checkAffordability", "suggestAffordableRange");

	private static final Logger log = LoggerFactory.getLogger(AdvisorOrchestrator.class);

	private final ObjectProvider<AdvisorAgent> advisor;
	private final ObjectProvider<VerifierAgent> verifier;
	private final ConversationService conversations;
	private final ProfileService profile;
	private final PropertyService property;
	private final ListingReader listings;
	private final BankCatalogService banks;
	private final LoanProfileAssembler loans;
	private final DocumentService documents;
	private final DocumentReader reader;
	private final Phases phases;
	private final BankThreads bankThreads;
	private final ReplyDrafts drafts;
	private final ObjectMapper objectMapper;

	public AdvisorOrchestrator(ObjectProvider<AdvisorAgent> advisor, ObjectProvider<VerifierAgent> verifier,
			ConversationService conversations,
			ProfileService profile, PropertyService property, ListingReader listings, BankCatalogService banks,
			LoanProfileAssembler loans, DocumentService documents, DocumentReader reader, Phases phases,
			BankThreads bankThreads, ReplyDrafts drafts, ObjectMapper objectMapper) {
		this.advisor = advisor;
		this.verifier = verifier;
		this.conversations = conversations;
		this.profile = profile;
		this.property = property;
		this.listings = listings;
		this.banks = banks;
		this.loans = loans;
		this.documents = documents;
		this.reader = reader;
		this.phases = phases;
		this.bankThreads = bankThreads;
		this.drafts = drafts;
		this.objectMapper = objectMapper;
	}

	public Flux<StreamEvent> send(UUID userId, String userText) {
		List<Message> earlier = conversations.history(userId);
		conversations.append(userId, Message.Role.USER, userText);
		List<Message> visible = visible(earlier);
		UserStatements stated = UserStatements.from(userTexts(visible), userText);
		return turn(userId, visible, new UserMessage(userText), userText, stated, null);
	}

	/**
	 * A question asked next to a bank's email thread in the inbox (#80): the same advisor and
	 * the same conversation as the chat, with both messages tagged with the thread. Only here
	 * can the advisor draft a reply to the bank.
	 *
	 * @throws NoSuchElementException if the user has no such thread
	 */
	public Flux<StreamEvent> assist(UUID userId, UUID threadId, String userText) {
		BankThreads.BankThread thread = bankThreads.find(userId, threadId).orElseThrow();
		List<Message> earlier = conversations.history(userId);
		conversations.append(userId, Message.Role.USER, userText, threadId);
		List<Message> visible = visible(earlier);
		List<String> said = new ArrayList<>(userTexts(visible));
		UserStatements stated = UserStatements.from(List.copyOf(said), userText);
		said.add(userText);
		return turn(userId, visible, new UserMessage(nextTo(thread.bank(), userText)), userText, stated,
				new AdvisorTools.Inbox(thread, drafts, said));
	}

	/**
	 * The {@code APPLICATION_SENT} trigger (#80): the banks have already answered, so the
	 * advisor tells the user what each one said.
	 *
	 * @throws NoSuchElementException if the user has no such application
	 */
	public Flux<StreamEvent> applicationSent(UUID userId, UUID applicationId) {
		List<BankThreads.BankThread> threads = bankThreads.ofApplication(userId, applicationId);
		if (threads.isEmpty()) {
			throw new NoSuchElementException("No such application");
		}
		String names = names(threads);
		return noted(userId, "Sent the application to " + names, "The user just sent their application to " + names
				+ ". Each bank has already answered; their emails are in the JSON. Tell the user in a few short "
				+ "lines what each bank said (an offer, a question or a decline), using only the figures in those "
				+ "emails, and that they can read and answer each bank in the inbox, where you can explain an offer "
				+ "or draft a reply with them.");
	}

	/**
	 * The {@code OFFER_ACCEPTED} trigger (#80): the next steps in real life, and an offer to
	 * help say no to the other banks.
	 *
	 * @throws NoSuchElementException if the user has no such thread
	 * @throws IllegalStateException  if its offer isn't accepted
	 */
	public Flux<StreamEvent> offerAccepted(UUID userId, UUID threadId) {
		BankThreads.BankThread accepted = bankThreads.find(userId, threadId).orElseThrow();
		if (accepted.status() != EmailThread.Status.ACCEPTED) {
			throw new IllegalStateException("This offer isn't accepted");
		}
		List<BankThreads.BankThread> others = bankThreads.all(userId).stream()
				.filter(thread -> !thread.threadId().equals(threadId)).toList();
		String note = "The user just accepted " + accepted.bank() + "'s offer. Congratulate them in one sentence, "
				+ "then sum up the next steps in real life: signing the loan contract and the purchase at a notary, "
				+ "paying the down payment, home insurance before the loan is paid out, and anything "
				+ accepted.bank() + "'s last email asks for.";
		if (!others.isEmpty()) {
			note += " Their threads with " + names(others) + " are now closed. Offer to draft a short, polite "
					+ "\"no thanks\" to each; they can ask you for it next to that bank's thread in the inbox.";
		}
		return noted(userId, "Accepted the offer from " + accepted.bank(), note);
	}

	private static String names(List<BankThreads.BankThread> threads) {
		List<String> names = threads.stream().map(BankThreads.BankThread::bank).toList();
		return names.size() == 1 ? names.getFirst()
				: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}

	/**
	 * A turn for something that happened outside the chat: a note the user sees in the
	 * conversation, and what the advisor is told in place of a user message.
	 */
	private Flux<StreamEvent> noted(UUID userId, String shown, String note) {
		List<Message> earlier = conversations.history(userId);
		conversations.append(userId, Message.Role.SYSTEM, shown);
		List<Message> visible = visible(earlier);
		UserStatements stated = UserStatements.from(userTexts(visible), "");
		return turn(userId, visible, new SystemMessage(note), note, stated, null);
	}

	/** How a message asked next to a bank's thread reads to the advisor. */
	private static String nextTo(String bank, String text) {
		return "[next to the email thread with " + bank + "] " + text;
	}

	/**
	 * The {@code DOCUMENT_UPLOADED} trigger (#72): the document reader reads the upload, its
	 * values go to the profile as uncertain, and the advisor asks the user to confirm them.
	 * Nothing the reader made up counts as the user's words, so the advisor can't confirm
	 * them itself.
	 *
	 * @throws java.util.NoSuchElementException if the user has no such document
	 * @throws IllegalStateException            if the document isn't waiting to be read
	 */
	public Flux<StreamEvent> documentUploaded(UUID userId, UUID documentId) {
		DocumentView document = documents.find(userId, documentId).orElseThrow();
		if (document.status() != Document.Status.UPLOADED || !document.values().isEmpty()) {
			throw new IllegalStateException("This document was already read");
		}
		List<Message> earlier = conversations.history(userId);
		conversations.append(userId, Message.Role.SYSTEM, "Uploaded " + document.fileName() + " as: " + document.label());
		List<Message> visible = visible(earlier);
		List<String> said = userTexts(visible);
		UserStatements stated = UserStatements.from(said, "");

		// The reader may call the LLM, so it runs off the request thread.
		return Flux.defer(() -> {
			DocumentReader.Reading reading = reader.read(userId, documentId, said);
			List<StreamEvent> events = new ArrayList<>();
			if (!reading.fields().isEmpty()) {
				events.add(StreamEvent.profileUpdated(reading.fields()));
			}
			events.add(StreamEvent.documentStatus(reading.document()));
			String note = uploadNote(reading);
			return Flux.fromIterable(events).concatWith(turn(userId, visible, new SystemMessage(note), note, stated, null));
		}).subscribeOn(Schedulers.boundedElastic())
				.onErrorResume(e -> {
					log.warn("Reading the uploaded document failed", e);
					return Flux.just(StreamEvent.error("Something went wrong. Please try again."));
				});
	}

	/** What the advisor is told about the upload, in place of a user message. */
	private static String uploadNote(DocumentReader.Reading reading) {
		DocumentView document = reading.document();
		String uploaded = "The user just uploaded their " + document.label().toLowerCase(Locale.ROOT) + " (file "
				+ document.fileName() + ", type " + document.type() + ").";
		if (!reading.read()) {
			return uploaded + " Its values couldn't be read. Tell the user, and ask them for "
					+ String.join(", ", document.type().reads().stream().map(ProfileKey::key).toList())
					+ " instead, one at a time.";
		}
		if (reading.fields().isEmpty()) {
			return uploaded + " There is nothing in it to confirm, so it is marked as confirmed. Thank them in one "
					+ "sentence and carry on.";
		}
		return uploaded + " The document reader read these values from it, now saved as uncertain: "
				+ String.join(", ", reading.fields().stream().map(f -> f.key() + "=" + f.value()).toList())
				+ ". Tell the user in plain words what you read and ask whether it's right. Don't confirm anything "
				+ "until they answer.";
	}

	/**
	 * One advisor turn: streams the reply with the events its tools push in between, then
	 * saves and checks it.
	 *
	 * @param current     what the advisor answers: the user's message or a note (an upload)
	 * @param currentText the same as text, for the checks
	 * @param inbox       the bank thread an inbox turn is about (#80), or null in the chat
	 */
	private Flux<StreamEvent> turn(UUID userId, List<Message> visible,
			org.springframework.ai.chat.messages.Message current, String currentText, UserStatements stated,
			AdvisorTools.Inbox inbox) {
		// Tools push their events here while the reply is still streaming.
		Sinks.Many<StreamEvent> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
		AdvisorTools tools = new AdvisorTools(userId, profile, property, listings, banks, loans, documents, phases,
				stated, event -> toolEvents.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1))),
				inbox);
		UUID threadId = inbox == null ? null : inbox.thread().threadId();

		StringBuilder reply = new StringBuilder();
		Flux<StreamEvent> text = replyText(userId, visible, current, tools)
				.doOnNext(reply::append)
				.map(StreamEvent::text)
				.doFinally(signal -> toolEvents.tryEmitComplete());

		return Flux.merge(text, toolEvents.asFlux())
				// The verifier is a blocking call, so the end of the turn runs off the stream's thread.
				.concatWith(Flux.defer(() -> finish(userId, threadId, reply.toString(), currentText, visible, tools))
						.subscribeOn(Schedulers.boundedElastic()))
				.onErrorResume(e -> {
					log.warn("Advisor turn failed", e);
					return Flux.just(StreamEvent.error("Something went wrong. Please try again."));
				});
	}

	private static List<String> userTexts(List<Message> messages) {
		return messages.stream().filter(m -> m.getRole() == Message.Role.USER).map(Message::getText).toList();
	}

	/**
	 * Saves the reply and, on a flagged turn, has the verifier check it (#67). A turn is
	 * flagged when the number check finds a figure that comes from nowhere, or when it
	 * talked about money. A rejected reply is replaced by the verifier's safe version.
	 */
	private Flux<StreamEvent> finish(UUID userId, UUID threadId, String reply, String userText,
			List<Message> earlier, AdvisorTools tools) {
		Long messageId = conversations.append(userId, Message.Role.ADVISOR, reply, threadId).getId();
		VerifierAgent checker = verifier.getIfAvailable();
		if (checker == null || reply.isBlank() || reply.equals(OFFLINE_REPLY)) {
			return Flux.just(StreamEvent.done(messageId));
		}
		try {
			NumberCheck.Result numbers = NumberCheck.check(reply, sources(userId, userText, earlier, tools));
			List<String> money = tools.used().stream().map(AdvisorTools.ToolUse::name)
					.filter(MONEY_TOOLS::contains).distinct().toList();
			if (numbers.passed() && money.isEmpty()) {
				return Flux.just(StreamEvent.done(messageId));
			}
			VerifierAgent.Verdict verdict = checker.verify(task(userText, numbers), reply,
					factsJson(userId, userText, earlier, tools));
			log.info("Advisor turn flagged (figures from nowhere: {}, money tools: {}): verifier {}",
					numbers.unsupported(), money, verdict.approved()
							? "approved" + (verdict.note() == null ? "" : " (" + verdict.note() + ")")
							: "replaced the reply");
			if (verdict.approved()) {
				return Flux.just(StreamEvent.done(messageId));
			}
			conversations.correct(messageId, verdict.output());
			return Flux.just(StreamEvent.corrected(messageId, verdict.output()), StreamEvent.done(messageId));
		} catch (RuntimeException e) {
			// Like the verifier itself, the check fails open: the reply has already been shown.
			log.warn("Checking the advisor's reply failed; it stands", e);
			return Flux.just(StreamEvent.done(messageId));
		}
	}

	/** Everything the advisor may take figures from this turn (see {@link NumberCheck}). */
	private List<String> sources(UUID userId, String userText, List<Message> earlier, AdvisorTools tools) {
		List<String> sources = new ArrayList<>();
		tools.used().forEach(use -> sources.add(use.result()));
		sources.add(profileJson(userId));
		// The user's own words, and earlier replies, which already passed this check.
		earlier.forEach(message -> sources.add(message.getText()));
		sources.add(userText);
		return sources;
	}

	private static String task(String userText, NumberCheck.Result numbers) {
		String task = "A mortgage advisor's chat reply to a home buyer, answering their message: \"" + userText
				+ "\". Figures may only come from the tool results, the profile, the property, the bank emails "
				+ "and the user's own messages in the facts.";
		return numbers.passed() ? task : task + " These figures in the draft appear in none of them: "
				+ String.join(", ", numbers.unsupported()) + ".";
	}

	private String factsJson(UUID userId, String userText, List<Message> earlier, AdvisorTools tools) {
		Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("profile", profile.fields(userId));
		facts.put("property", property.active(userId).orElse(null));
		facts.put("bankEmails", bankThreads.all(userId));
		facts.put("toolResults", tools.used().stream().map(use -> use.name() + ": " + use.result()).toList());
		List<String> said = new ArrayList<>(earlier.stream()
				.filter(m -> m.getRole() == Message.Role.USER).map(Message::getText).toList());
		said.add(userText);
		facts.put("userMessages", said);
		return objectMapper.writeValueAsString(facts);
	}

	private Flux<String> replyText(UUID userId, List<Message> earlier,
			org.springframework.ai.chat.messages.Message current, AdvisorTools tools) {
		AdvisorAgent agent = advisor.getIfAvailable();
		if (agent == null) {
			return Flux.just(OFFLINE_REPLY);
		}
		return Flux.defer(() -> agent.reply(toPrompt(userId, earlier), current, () -> profileJson(userId), tools))
				.onErrorResume(TokenBudgetExceededException.class, e -> Flux.just(OFFLINE_REPLY));
	}

	/** The part of the conversation the advisor sees, to keep prompts bounded. */
	private static List<Message> visible(List<Message> earlier) {
		return earlier.subList(Math.max(0, earlier.size() - HISTORY_LIMIT), earlier.size());
	}

	/** The conversation as the advisor sees it; what the user asked in the inbox says which bank's thread it was. */
	private List<org.springframework.ai.chat.messages.Message> toPrompt(UUID userId, List<Message> earlier) {
		Map<UUID, String> threadBanks = new LinkedHashMap<>();
		if (earlier.stream().anyMatch(m -> m.getThreadId() != null)) {
			bankThreads.all(userId).forEach(thread -> threadBanks.put(thread.threadId(), thread.bank()));
		}
		return earlier.stream()
				.<org.springframework.ai.chat.messages.Message>map(m -> switch (m.getRole()) {
					case USER -> new UserMessage(threadBanks.containsKey(m.getThreadId())
							? nextTo(threadBanks.get(m.getThreadId()), m.getText()) : m.getText());
					case ADVISOR -> new AssistantMessage(m.getText());
					case SYSTEM -> new SystemMessage(m.getText());
				})
				.toList();
	}

	private String profileJson(UUID userId) {
		Map<String, Object> known = new LinkedHashMap<>();
		known.put("fields", profile.fields(userId));
		known.put("property", property.active(userId).orElse(null));
		known.put("documents", documents.checklist(userId).stream()
				.filter(document -> document.status() != Document.Status.NOT_NEEDED_YET)
				.map(document -> document.type() + ": " + document.status().name().toLowerCase(Locale.ROOT))
				.toList());
		List<BankThreads.BankThread> threads = bankThreads.all(userId);
		if (!threads.isEmpty()) {
			known.put("bankEmails", threads);
		}
		return objectMapper.writeValueAsString(known);
	}
}
