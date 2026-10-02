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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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

	/** Steps the user has continued past (submitted with advance), in no particular order. */
	@Convert(converter = StringSetConverter.class)
	@Column(nullable = false, length = 1000)
	private Set<String> completedSteps = new LinkedHashSet<>();

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

	/** Drops answers for the given keys, e.g. fields of a branch that is no longer on the path. */
	public void removeAnswers(Collection<String> keys) {
		keys.forEach(this.answers::remove);
	}

	public Set<String> getCompletedSteps() {
		return completedSteps;
	}

	public void markCompleted(String stepId) {
		this.completedSteps.add(stepId);
	}

	/** Forgets completion for any step not in {@code stepIds}. */
	public void retainCompleted(Collection<String> stepIds) {
		this.completedSteps.retainAll(stepIds);
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
