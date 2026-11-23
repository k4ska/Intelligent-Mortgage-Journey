package com.seb.mortgage.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the user's saved chats with the advisor (#160). Which one is open is kept in
 * {@link ActiveConversation}; "start over" wipes them all.
 */
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

	/** When a message was last added; the history sidebar lists the newest first. */
	@Column(nullable = false)
	private Instant updatedAt;

	/** When the advisor first gave the user an affordability result in this chat; it moves the conversation on (#73). */
	private Instant affordabilityDiscussedAt;

	protected Conversation() {
	}

	public Conversation(UUID userId) {
		this.userId = userId;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getAffordabilityDiscussedAt() {
		return affordabilityDiscussedAt;
	}

	/** A message was added. */
	public void touched() {
		updatedAt = Instant.now();
	}

	/** Only the first time counts. */
	public void affordabilityDiscussed() {
		if (affordabilityDiscussedAt == null) {
			affordabilityDiscussedAt = Instant.now();
		}
	}
}
