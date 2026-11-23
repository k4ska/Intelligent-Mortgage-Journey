package com.seb.mortgage.conversation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * A user's saved chats with the advisor (#160). One of them is open at a time, and every
 * existing caller (the chat, the inbox advisor, triggers) works on that one. A new chat
 * starts with a fixed greeting (no LLM call).
 */
@Service
public class ConversationService {

	public static final String GREETING = "Hi, how can I help you?";

	public record Summary(UUID id, String title, Instant createdAt, Instant updatedAt, boolean active) {}

	private static final String UNTITLED = "New conversation";

	private final ConversationRepository conversations;
	private final ActiveConversationRepository activeConversations;
	private final MessageRepository messages;

	public ConversationService(ConversationRepository conversations, ActiveConversationRepository activeConversations,
			MessageRepository messages) {
		this.conversations = conversations;
		this.activeConversations = activeConversations;
		this.messages = messages;
	}

	/**
	 * The open chat's messages, oldest first, plus what was asked next to a bank's email
	 * thread in any chat: the bank threads belong to the account, not to one chat, so their
	 * advisor messages stay with them when the user switches chats. Creates the first chat
	 * with the greeting if needed.
	 */
	@Transactional
	public List<Message> history(UUID userId) {
		return messages.findChatWithThreadMessages(conversationFor(userId).getId(), userId);
	}

	/** The open chat; the first one is created with the greeting if needed. */
	@Transactional
	public UUID activeId(UUID userId) {
		return conversationFor(userId).getId();
	}

	/** The user's chats, most recently updated first. */
	@Transactional
	public List<Summary> list(UUID userId) {
		UUID selected = conversationFor(userId).getId();
		return conversations.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId).stream()
				.map(conversation -> summary(conversation, conversation.getId().equals(selected)))
				.toList();
	}

	/**
	 * Opens a new chat; the others stay as they are. If the open chat is still just the
	 * greeting, it is the new chat, so pressing the button twice doesn't leave empty chats.
	 */
	@Transactional
	public Summary create(UUID userId) {
		Conversation current = conversationFor(userId);
		if (messages.countByConversationId(current.getId()) <= 1) {
			return summary(current, true);
		}
		Conversation created = createWithGreeting(userId);
		activeConversations.findById(userId).ifPresentOrElse(
				active -> active.select(created.getId()),
				() -> activeConversations.save(new ActiveConversation(userId, created.getId())));
		return summary(created, true);
	}

	/** @throws NoSuchElementException if the user has no such chat (another user's id included) */
	@Transactional
	public void select(UUID userId, UUID conversationId) {
		conversations.findByIdAndUserId(conversationId, userId)
				.orElseThrow(() -> new NoSuchElementException("Conversation not found"));
		activeConversations.findById(userId).ifPresentOrElse(
				active -> active.select(conversationId),
				() -> activeConversations.save(new ActiveConversation(userId, conversationId)));
	}

	@Transactional
	public Message append(UUID userId, Message.Role role, String text) {
		return append(userId, role, text, null);
	}

	/** @param threadId the bank thread the message was written next to (#80), or null in the chat */
	@Transactional
	public Message append(UUID userId, Message.Role role, String text, UUID threadId) {
		Conversation conversation = conversationFor(userId);
		Message saved = messages.save(new Message(conversation.getId(), role, text, threadId));
		conversation.touched();
		return saved;
	}

	/** Deletes every saved chat (start over); the next {@link #history} starts again with the greeting. */
	@Transactional
	public void delete(UUID userId) {
		activeConversations.deleteById(userId);
		for (Conversation conversation : conversations.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId)) {
			messages.deleteByConversationId(conversation.getId());
			conversations.delete(conversation);
		}
	}

	/** Saves the chart the advisor showed with a reply, so it is still there after a reload (#130). */
	@Transactional
	public void attachChart(Long messageId, String chartJson) {
		messages.findById(messageId).ifPresent(message -> message.attachChart(chartJson));
	}

	/** Saves the verifier's safe version of a reply in place of the original (#67). */
	@Transactional
	public void correct(Long messageId, String text) {
		messages.findById(messageId).ifPresent(message -> message.correct(text));
	}

	/** The advisor gave the user an affordability result in the open chat (#73). */
	@Transactional
	public void affordabilityDiscussed(UUID userId) {
		conversationFor(userId).affordabilityDiscussed();
	}

	@Transactional(readOnly = true)
	public boolean hasDiscussedAffordability(UUID userId) {
		return activeConversations.findById(userId)
				.flatMap(active -> conversations.findByIdAndUserId(active.getConversationId(), userId))
				.map(conversation -> conversation.getAffordabilityDiscussedAt() != null)
				.orElse(false);
	}

	private Summary summary(Conversation conversation, boolean active) {
		return new Summary(conversation.getId(), title(conversation.getId()), conversation.getCreatedAt(),
				conversation.getUpdatedAt(), active);
	}

	/** The chat's first message the user typed in the chat itself, shortened. */
	private String title(UUID conversationId) {
		return messages.findFirstByConversationIdAndRoleAndThreadIdIsNullOrderByIdAsc(
				conversationId, Message.Role.USER)
				.map(message -> message.getText().replaceAll("\\s+", " ").trim())
				.filter(text -> !text.isEmpty())
				.map(text -> text.length() > 60 ? text.substring(0, 60) + "…" : text)
				.orElse(UNTITLED);
	}

	private Conversation conversationFor(UUID userId) {
		return activeConversations.findById(userId)
				.flatMap(active -> conversations.findByIdAndUserId(active.getConversationId(), userId))
				.orElseGet(() -> {
					Conversation created = createWithGreeting(userId);
					activeConversations.save(new ActiveConversation(userId, created.getId()));
					return created;
				});
	}

	private Conversation createWithGreeting(UUID userId) {
		Conversation created = conversations.save(new Conversation(userId));
		messages.save(new Message(created.getId(), Message.Role.ADVISOR, GREETING));
		return created;
	}
}
