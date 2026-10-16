package com.seb.mortgage.bank;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email")
public class Email {
	public enum Sender { BANK, USER }
	@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
	@Column(nullable = false) private UUID threadId;
	@Enumerated(EnumType.STRING) @Column(name = "email_from", nullable = false) private Sender from;
	@Column(nullable = false) private String type;
	@Column(nullable = false) private String subject;
	@Lob @Column(nullable = false, columnDefinition = "CLOB") private String body;
	@Column(nullable = false, updatable = false) private Instant createdAt;
	protected Email() {}
	public Email(UUID threadId, Sender from, String type, String subject, String body) {
		this.threadId = threadId; this.from = from; this.type = type; this.subject = subject;
		this.body = body; this.createdAt = Instant.now();
	}
	public UUID getId() { return id; }
	public UUID getThreadId() { return threadId; }
	public Sender getFrom() { return from; }
	public String getType() { return type; }
	public String getSubject() { return subject; }
	public String getBody() { return body; }
	public Instant getCreatedAt() { return createdAt; }
}