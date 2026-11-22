package com.seb.mortgage.conversation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Persists multiple advisor chats per user, with one active chat for existing API callers. */
@Service
public class ConversationService {

    public static final String GREETING = "Hi, how can I help you?";

    public record Summary(UUID id, String title, Instant createdAt, Instant updatedAt, boolean active) {}

    private final ConversationRepository conversations;
    private final ActiveConversationRepository activeConversations;
    private final MessageRepository messages;

    public ConversationService(ConversationRepository conversations, ActiveConversationRepository activeConversations,
            MessageRepository messages) {
        this.conversations = conversations;
        this.activeConversations = activeConversations;
        this.messages = messages;
    }

    /** Oldest first; a first visit starts the chat with a fixed greeting. */
    @Transactional
    public List<Message> history(UUID userId) {
        return messages.findByConversationIdOrderByIdAsc(conversationFor(userId).getId());
    }

    /** Lists only the authenticated user's chats, most recently updated first. */
    @Transactional
    public List<Summary> list(UUID userId) {
        UUID selected = conversationFor(userId).getId();
        return conversations.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId).stream()
                .map(conversation -> new Summary(conversation.getId(), title(conversation.getId()),
                        conversation.getCreatedAt(), conversation.getUpdatedAt(), conversation.getId().equals(selected)))
                .toList();
    }

    /** Save the current chat and start another one; do not erase any existing messages. */
    @Transactional
    public Summary create(UUID userId) {
        Conversation created = createWithGreeting(userId);
        activeConversations.findById(userId).ifPresentOrElse(
                active -> active.select(created.getId()),
                () -> activeConversations.save(new ActiveConversation(userId, created.getId())));
        return new Summary(created.getId(), "New conversation", created.getCreatedAt(), created.getUpdatedAt(), true);
    }

    /** Switching never accepts another user's conversation ID. */
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

    /** @param threadId bank inbox thread, or null in the main chat */
    @Transactional
    public Message append(UUID userId, Message.Role role, String text, UUID threadId) {
        Conversation conversation = conversationFor(userId);
        Message saved = messages.save(new Message(conversation.getId(), role, text, threadId));
        conversation.touched();
        return saved;
    }

    /** Used by Start over: deletes every saved chat, not only the active one. */
    @Transactional
    public void delete(UUID userId) {
        activeConversations.deleteById(userId);
        for (Conversation conversation : conversations.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId)) {
            messages.deleteByConversationId(conversation.getId());
            conversations.delete(conversation);
        }
    }

    @Transactional
    public void attachChart(Long messageId, String chartJson) {
        messages.findById(messageId).ifPresent(message -> message.attachChart(chartJson));
    }

    @Transactional
    public void correct(Long messageId, String text) {
        messages.findById(messageId).ifPresent(message -> message.correct(text));
    }

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

    private String title(UUID conversationId) {
        return messages.findFirstByConversationIdAndRoleAndThreadIdIsNullOrderByIdAsc(
                conversationId, Message.Role.USER)
                .map(message -> message.getText().replaceAll("\\s+", " ").trim())
                .filter(text -> !text.isEmpty())
                .map(text -> text.length() > 60 ? text.substring(0, 60) + "…" : text)
                .orElse("New conversation");
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