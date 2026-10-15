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

	@Override
	public List<ProfileFieldView> snapshot(UUID userId) {
		return fields(userId);
	}
}
