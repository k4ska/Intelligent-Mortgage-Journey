package com.seb.mortgage.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A saved advisor chat. The active chat is tracked separately for each user. */
@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** When the advisor first gave an affordability result in this chat (#73). */
    private Instant affordabilityDiscussedAt;

    protected Conversation() {
    }

    public Conversation(UUID userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAffordabilityDiscussedAt() { return affordabilityDiscussedAt; }

    public void touched() { updatedAt = Instant.now(); }

    /** Only the first time counts. */
    public void affordabilityDiscussed() {
        if (affordabilityDiscussedAt == null) affordabilityDiscussedAt = Instant.now();
    }
}