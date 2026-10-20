package com.seb.mortgage.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in a conversation, from the user or the advisor. The id is an increasing
 * number, so it also gives the order (two messages can share a timestamp).
 */
@Entity
@Table(name = "message")
public class Message {

	/** SYSTEM notes something that happened outside the chat, like an upload (#72). */
	public enum Role { USER, ADVISOR, SYSTEM }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private UUID conversationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(nullable = false)
	private boolean corrected;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Message() {
	}

	public Message(UUID conversationId, Role role, String text) {
		this.conversationId = conversationId;
		this.role = role;
		this.text = text;
		this.createdAt = Instant.now();
	}

	/** Replaces an advisor reply with the verifier's safe version (#67). */
	public void correct(String text) {
		this.text = text;
		this.corrected = true;
	}

	public Long getId() {
		return id;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public Role getRole() {
		return role;
	}

	public String getText() {
		return text;
	}

	public boolean isCorrected() {
		return corrected;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
