package com.seb.mortgage.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService implements ProfileSnapshotProvider {

	private final ProfileFieldRepository fields;

	public ProfileService(ProfileFieldRepository fields) {
		this.fields = fields;
	}

	/** The user's fields in {@link ProfileKey} order. */
	@Transactional(readOnly = true)
	public List<ProfileFieldView> fields(UUID userId) {
		return fields.findByUserId(userId).stream()
				.sorted(Comparator.comparing(ProfileField::getKey))
				.map(ProfileField::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public boolean has(UUID userId, ProfileKey key) {
		return fields.findByUserIdAndKey(userId, key).isPresent();
	}

	/** Saves a value, replacing any earlier value for the same key. It must already be normalized. */
	@Transactional
	public ProfileFieldView save(UUID userId, ProfileKey key, String value,
			ProfileField.Source source, ProfileField.Status status) {
		ProfileField field = fields.findByUserIdAndKey(userId, key)
				.orElseGet(() -> new ProfileField(userId, key));
		field.set(value, source, status);
		return fields.save(field).view();
	}

	/** A value the document reader read: uncertain until the user confirms it (#72). */
	@Transactional
	public ProfileFieldView saveFromDocument(UUID userId, ProfileKey key, String value, String document) {
		ProfileField field = fields.findByUserIdAndKey(userId, key)
				.orElseGet(() -> new ProfileField(userId, key));
		field.set(value, ProfileField.Source.DOCUMENT, ProfileField.Status.UNCERTAIN, document);
		return fields.save(field).view();
	}

	/** The user said the values read from this document are right; returns the fields now confirmed. */
	@Transactional
	public List<ProfileFieldView> confirmFromDocument(UUID userId, String document) {
		List<ProfileField> read = fields.findByUserId(userId).stream()
				.filter(field -> isUncertainFrom(field, document))
				.sorted(Comparator.comparing(ProfileField::getKey))
				.toList();
		read.forEach(ProfileField::confirm);
		return read.stream().map(ProfileField::view).toList();
	}

	/** Whether a value read from this document still waits for the user to confirm it. */
	@Transactional(readOnly = true)
	public boolean hasUncertainFrom(UUID userId, String document) {
		return fields.findByUserId(userId).stream().anyMatch(field -> isUncertainFrom(field, document));
	}

	private static boolean isUncertainFrom(ProfileField field, String document) {
		return field.getSource() == ProfileField.Source.DOCUMENT && field.getStatus() == ProfileField.Status.UNCERTAIN
				&& document.equals(field.getSourceDocument());
	}

	@Override
	public List<ProfileFieldView> snapshot(UUID userId) {
		return fields(userId);
	}
}
