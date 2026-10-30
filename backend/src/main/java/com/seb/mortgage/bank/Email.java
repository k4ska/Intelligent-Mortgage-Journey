package com.seb.mortgage.bank;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "email")
public class Email {
	public enum Sender { BANK, USER }
	@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
	@Column(nullable = false) private UUID threadId;
	@Enumerated(EnumType.STRING) @Column(name = "email_from", nullable = false) private Sender from;
	@Column(nullable = false) private String type;
	@Column(nullable = false) private String subject;
	@Column(nullable = false, columnDefinition = "TEXT") private String body;
	@Column(name = "offer_json", columnDefinition = "TEXT") private String offerJson;
	@Column(nullable = false, updatable = false) private Instant createdAt;
	protected Email() {}
	public Email(UUID threadId, Sender from, String type, String subject, String body) {
		this(threadId, from, type, subject, body, null);
	}
	public Email(UUID threadId, Sender from, String type, String subject, String body, String offerJson) {
		this.threadId = threadId; this.from = from; this.type = type; this.subject = subject;
		this.body = body; this.offerJson = offerJson; this.createdAt = Instant.now();
	}
	public UUID getId() { return id; }
	public UUID getThreadId() { return threadId; }
	public Sender getFrom() { return from; }
	public String getType() { return type; }
	public String getSubject() { return subject; }
	public String getBody() { return body; }
	public String getOfferJson() { return offerJson; }
	public Instant getCreatedAt() { return createdAt; }
}
