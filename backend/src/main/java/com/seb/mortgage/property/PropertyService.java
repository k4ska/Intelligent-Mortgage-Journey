package com.seb.mortgage.property;

import com.seb.mortgage.profile.ProfileField;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PropertyService implements PropertySnapshotProvider {

	private final PropertyRepository properties;

	public PropertyService(PropertyRepository properties) {
		this.properties = properties;
	}

	/** The user's property, if they have one in mind yet. */
	@Transactional(readOnly = true)
	public Optional<PropertyView> active(UUID userId) {
		return properties.findByUserId(userId).map(Property::view);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PropertyView> snapshot(UUID userId) {
		return active(userId);
	}

	/** Replaces the user's property with what a listing says. The values must already be normalized. */
	@Transactional
	public PropertyView replaceFromListing(UUID userId, String listingUrl, Map<PropertyKey, String> values) {
		Property property = findOrCreate(userId);
		property.replace(listingUrl);
		// The listing's structured data is the seller's own; nothing for the user to confirm.
		values.forEach((key, value) -> property.set(key, value, ProfileField.Source.LISTING, ProfileField.Status.CONFIRMED));
		return properties.save(property).view();
	}

	/**
	 * Saves values from the chat, replacing the same values from earlier. The values must
	 * already be normalized.
	 *
	 * @param newProperty true to forget the old property first (the user has another home in mind)
	 */
	@Transactional
	public PropertyView updateFromChat(UUID userId, Map<PropertyKey, String> values, ProfileField.Status status,
			boolean newProperty) {
		Property property = findOrCreate(userId);
		if (newProperty) {
			property.replace(null);
		}
		values.forEach((key, value) -> property.set(key, value, ProfileField.Source.CHAT, status));
		return properties.save(property).view();
	}

	/** The required values the user's property doesn't have yet (all of them if there's no property). */
	@Transactional(readOnly = true)
	public List<PropertyKey> missing(UUID userId) {
		Optional<Property> property = properties.findByUserId(userId);
		return Arrays.stream(PropertyKey.values())
				.filter(PropertyKey::required)
				.filter(key -> property.map(p -> !p.has(key)).orElse(true))
				.toList();
	}

	private Property findOrCreate(UUID userId) {
		return properties.findByUserId(userId).orElseGet(() -> new Property(userId));
	}
}
