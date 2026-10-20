package com.seb.mortgage.documents;

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
import jakarta.persistence.UniqueConstraint;

/**
 * One document a bank will need, for one user: at most one per type. Only the file name is
 * kept; the file itself is never stored or read (the document reader invents its values).
 */
@Entity
@Table(name = "document", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "doc_type" }))
public class Document {

	/**
	 * {@code NOT_NEEDED_YET} is only ever shown in the checklist, for a document nobody has
	 * asked for yet; a stored document is needed, uploaded or confirmed.
	 */
	public enum Status { NOT_NEEDED_YET, NEEDED, UPLOADED, CONFIRMED }

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "doc_type", nullable = false)
	private DocumentType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column
	private String fileName;

	/** The values the document reader read, as JSON ({"netIncome":"2350"}); null until it has read them. */
	@Column(columnDefinition = "TEXT")
	private String readValues;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Document() {
	}

	public Document(UUID userId, DocumentType type) {
		this.userId = userId;
		this.type = type;
		this.status = Status.NEEDED;
		this.updatedAt = Instant.now();
	}

	/** A new file replaces the old one, and what was read from the old one no longer counts. */
	void uploaded(String fileName) {
		this.fileName = fileName;
		this.readValues = null;
		this.status = Status.UPLOADED;
		this.updatedAt = Instant.now();
	}

	void read(String readValues) {
		this.readValues = readValues;
		this.updatedAt = Instant.now();
	}

	void confirm() {
		this.status = Status.CONFIRMED;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public DocumentType getType() {
		return type;
	}

	public Status getStatus() {
		return status;
	}

	public String getFileName() {
		return fileName;
	}

	public String getReadValues() {
		return readValues;
	}
}
