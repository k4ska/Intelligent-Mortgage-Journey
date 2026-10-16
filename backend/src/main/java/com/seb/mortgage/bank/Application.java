package com.seb.mortgage.bank;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "mortgage_application")
@SuppressWarnings("unused")
public class Application {
	@Id @GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(nullable = false) private UUID userId;
	@Lob @Column(nullable = false, columnDefinition = "CLOB") private String snapshotJson;
	@Lob @Column(nullable = false, columnDefinition = "CLOB") private String bankIdsJson;
	@Column(nullable = false, updatable = false) private Instant createdAt;

	protected Application() {}
	public Application(UUID userId, String snapshotJson, String bankIdsJson) {
		this.userId = userId;
		this.snapshotJson = snapshotJson;
		this.bankIdsJson = bankIdsJson;
		this.createdAt = Instant.now();
	}
	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public String getSnapshotJson() { return snapshotJson; }
	public String getBankIdsJson() { return bankIdsJson; }
	public Instant getCreatedAt() { return createdAt; }
}
