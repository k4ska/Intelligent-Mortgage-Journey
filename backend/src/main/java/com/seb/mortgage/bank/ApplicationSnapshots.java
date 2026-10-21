package com.seb.mortgage.bank;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.seb.mortgage.documents.DocumentSnapshotProvider;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileSnapshotProvider;
import com.seb.mortgage.property.PropertySnapshotProvider;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Freezes what the user has now into an application (#86), and reads a frozen one back: the
 * profile fields, the property and the uploaded documents, as {@code { fields, property,
 * documents }}. It reads the profile, property and documents only through their snapshot
 * interfaces, which keeps the wall ({@code docs/architecture.md} §1).
 */
@Component
public class ApplicationSnapshots {

	private final ProfileSnapshotProvider profiles;
	private final PropertySnapshotProvider properties;
	private final DocumentSnapshotProvider documents;
	private final ObjectMapper mapper;

	public ApplicationSnapshots(ProfileSnapshotProvider profiles, PropertySnapshotProvider properties,
			DocumentSnapshotProvider documents, ObjectMapper mapper) {
		this.profiles = profiles;
		this.properties = properties;
		this.documents = documents;
		this.mapper = mapper;
	}

	/** The user's application as it is right now, as JSON to store. */
	public String freeze(UUID userId) {
		OfferCalculator.Snapshot snapshot = new OfferCalculator.Snapshot(profiles.snapshot(userId),
				properties.snapshot(userId).orElse(null),
				documents.snapshot(userId).stream()
						.map(document -> new OfferCalculator.DocumentSnapshot(document.type().name(),
								document.status().name()))
						.toList());
		try {
			return mapper.writeValueAsString(snapshot);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not freeze the application snapshot", e);
		}
	}

	/**
	 * Reads a frozen application. Applications sent before #86 stored only the list of
	 * profile fields; they read as that, with no property and no documents.
	 *
	 * @throws IllegalArgumentException if the JSON is neither shape
	 */
	public OfferCalculator.Snapshot read(String json) {
		try {
			JsonNode node = mapper.readTree(json);
			if (node.isArray()) {
				return new OfferCalculator.Snapshot(mapper.convertValue(node,
						new TypeReference<List<ProfileFieldView>>() {}), null);
			}
			return mapper.treeToValue(node, OfferCalculator.Snapshot.class);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Not an application snapshot", e);
		}
	}
}
