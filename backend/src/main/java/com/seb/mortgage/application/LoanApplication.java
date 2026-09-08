package com.seb.mortgage.application;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single in-progress (or completed) mortgage application. Answers are stored as a
 * free-form key/value map so the flow definition can add fields without a schema change.
 */
@Entity
@Table(name = "loan_application")
public class LoanApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Id of the step the user is currently on. */
	@Column(nullable = false)
	private String currentStepId;

	@Column(nullable = false)
	private boolean complete = false;

	@Lob
	@Convert(converter = JsonMapConverter.class)
	@Column(columnDefinition = "CLOB")
	private Map<String, Object> answers = new LinkedHashMap<>();

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected LoanApplication() {
	}

	public LoanApplication(String currentStepId) {
		this.currentStepId = currentStepId;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public void putAnswers(Map<String, Object> incoming) {
		if (incoming != null) {
			this.answers.putAll(incoming);
		}
	}

	public UUID getId() {
		return id;
	}

	public String getCurrentStepId() {
		return currentStepId;
	}

	public void setCurrentStepId(String currentStepId) {
		this.currentStepId = currentStepId;
	}

	public boolean isComplete() {
		return complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	public Map<String, Object> getAnswers() {
		return answers;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
