package com.seb.mortgage.property;

import com.seb.mortgage.profile.ProfileFieldView;

import java.util.List;

/**
 * The property as the API and the {@code property_updated} event show it. Its values have
 * the same shape as profile fields, so the panel can show both the same way.
 *
 * @param listingUrl the kv.ee link the property was read from, or null
 * @param fields     the values known so far, in {@link PropertyKey} order
 */
public record PropertyView(String listingUrl, List<ProfileFieldView> fields) {}
