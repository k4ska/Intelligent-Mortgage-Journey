package com.seb.mortgage.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * One fact in a user's profile, with where it came from and whether the user has
 * confirmed it. One row per fact makes "where did this come from" easy for every field
 * (see {@code docs/architecture.md} §4).
 */
@Entity
@Table(name = "profile_field", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "field_key" }))
public class ProfileField {

	/** Where a value came from. */
	public enum Source { CHAT, LISTING, DOCUMENT }

	/** Values read from a document stay uncertain until the user confirms them. */
	public enum Status { UNCERTAIN, CONFIRMED }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_key", nullable = false)
	private ProfileKey key;

	@Column(name = "field_value", nullable = false, length = 500)
	private String value;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Source source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ProfileField() {
	}

	public ProfileField(UUID userId, ProfileKey key) {
		this.userId = userId;
		this.key = key;
	}

	public void set(String value, Source source, Status status) {
		this.value = value;
		this.source = source;
		this.status = status;
		this.updatedAt = Instant.now();
	}

	public UUID getUserId() {
		return userId;
	}

	public ProfileKey getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public Source getSource() {
		return source;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public ProfileFieldView view() {
		return new ProfileFieldView(key.key(), value, source, status);
	}
}
