package com.seb.mortgage.property;

import java.util.Optional;
import java.util.UUID;

/**
 * What the bank side may read of the user's property: the one they have in mind right now,
 * to freeze into an application (#86). Like {@code ProfileSnapshotProvider}, it keeps the
 * bank code away from the advisor's tables ({@code docs/architecture.md} §1).
 */
public interface PropertySnapshotProvider {

	/** The user's property as it is now, or empty if they have none. */
	Optional<PropertyView> snapshot(UUID userId);
}
