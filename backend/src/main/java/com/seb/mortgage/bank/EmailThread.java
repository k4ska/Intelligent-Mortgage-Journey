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
@Table(name = "email_thread")
public class EmailThread {
	public enum Status { OPEN, OFFER, NEGOTIATING, ACCEPTED, DECLINED, CLOSED }
	@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
	@Column(nullable = false) private UUID applicationId;
	@Column(nullable = false) private UUID userId;
	@Column(nullable = false) private String bankId;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
	@Column(nullable = false) private boolean unread;
	@Column(nullable = false, updatable = false) private Instant createdAt;
	protected EmailThread() {}
	public EmailThread(UUID applicationId, UUID userId, String bankId) {
		this.applicationId = applicationId; this.userId = userId; this.bankId = bankId;
		this.status = Status.OPEN; this.unread = true; this.createdAt = Instant.now();
	}
	public UUID getId() { return id; }
	public UUID getApplicationId() { return applicationId; }
	public UUID getUserId() { return userId; }
	public String getBankId() { return bankId; }
	public Status getStatus() { return status; }
	public boolean isUnread() { return unread; }
	public Instant getCreatedAt() { return createdAt; }
	public void markRead() { unread = false; }
}