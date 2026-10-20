package com.seb.mortgage.conversation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A user's conversation with the advisor. The first time a user opens the chat, it starts
 * with a fixed greeting (no LLM call).
 */
@Service
public class ConversationService {

	public static final String GREETING = "Hi, how can I help you?";

	private final ConversationRepository conversations;
	private final MessageRepository messages;

	public ConversationService(ConversationRepository conversations, MessageRepository messages) {
		this.conversations = conversations;
		this.messages = messages;
	}

	/** The user's messages, oldest first. Creates the conversation with the greeting if needed. */
	@Transactional
	public List<Message> history(UUID userId) {
		return messages.findByConversationIdOrderByIdAsc(conversationFor(userId).getId());
	}

	@Transactional
	public Message append(UUID userId, Message.Role role, String text) {
		return messages.save(new Message(conversationFor(userId).getId(), role, text));
	}

	/** Saves the verifier's safe version of a reply in place of the original (#67). */
	@Transactional
	public void correct(Long messageId, String text) {
		messages.findById(messageId).ifPresent(message -> message.correct(text));
	}

	private Conversation conversationFor(UUID userId) {
		return conversations.findByUserId(userId).orElseGet(() -> {
			Conversation conversation = conversations.save(new Conversation(userId));
			messages.save(new Message(conversation.getId(), Message.Role.ADVISOR, GREETING));
			return conversation;
		});
	}
}
