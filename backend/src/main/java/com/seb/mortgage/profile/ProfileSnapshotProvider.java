package com.seb.mortgage.profile;

import java.util.List;
import java.util.UUID;

/**
 * What the bank side may read of a user's profile: the fields as they are right now, to
 * freeze into an application. The bank code depends on this interface only, never on the
 * advisor's tables, which keeps the wall between the two sides (see
 * {@code docs/architecture.md} §1).
 */
public interface ProfileSnapshotProvider {

	List<ProfileFieldView> snapshot(UUID userId);
}
