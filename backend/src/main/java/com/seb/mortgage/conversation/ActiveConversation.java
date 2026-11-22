package com.seb.mortgage.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One active chat per user, without restricting how many saved chats they can have. */
@Entity
@Table(name = "active_conversation")
public class ActiveConversation {

    @Id
    private UUID userId;

    @Column(nullable = false)
    private UUID conversationId;

    protected ActiveConversation() {
    }

    public ActiveConversation(UUID userId, UUID conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
    }

    public UUID getUserId() { return userId; }
    public UUID getConversationId() { return conversationId; }
    public void select(UUID conversationId) { this.conversationId = conversationId; }
}