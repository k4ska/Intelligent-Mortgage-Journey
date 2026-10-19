package com.seb.mortgage.property;

import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileFieldView;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The home the user has in mind. A user has at most one: a new property replaces the old
 * one in place. Each value remembers where it came from and whether the user has
 * confirmed it, like a profile field (see {@code docs/architecture.md} §4).
 */
@Entity
@Table(name = "property")
public class Property {

	/** One value with its source and status. */
	@Embeddable
	public record Value(
			@Column(name = "value_text", nullable = false, length = 500) String value,
			@Enumerated(EnumType.STRING) @Column(nullable = false) ProfileField.Source source,
			@Enumerated(EnumType.STRING) @Column(nullable = false) ProfileField.Status status) {}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	/** The kv.ee link the values were read from, or null when they all came from the chat. */
	@Column(length = 500)
	private String listingUrl;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "property_value", joinColumns = @JoinColumn(name = "property_id"))
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "value_key")
	private Map<PropertyKey, Value> values = new HashMap<>();

	@Column(nullable = false)
	private Instant updatedAt;

	protected Property() {
	}

	public Property(UUID userId) {
		this.userId = userId;
		this.updatedAt = Instant.now();
	}

	/** Forgets everything about the old property, to start on a new one. */
	public void replace(String listingUrl) {
		this.listingUrl = listingUrl;
		this.values.clear();
		this.updatedAt = Instant.now();
	}

	public void set(PropertyKey key, String value, ProfileField.Source source, ProfileField.Status status) {
		values.put(key, new Value(value, source, status));
		this.updatedAt = Instant.now();
	}

	public boolean has(PropertyKey key) {
		return values.containsKey(key);
	}

	public PropertyView view() {
		return new PropertyView(listingUrl, values.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> new ProfileFieldView(e.getKey().key(), e.getValue().value(),
						e.getValue().source(), e.getValue().status()))
				.toList());
	}
}
