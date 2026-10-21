package com.seb.mortgage.documents;

import java.util.List;
import java.util.UUID;

/**
 * What the bank side may read of the user's documents, to freeze into an application (#86):
 * only the ones the user uploaded, confirmed or not. A document the advisor asked for but
 * nobody uploaded isn't part of the application.
 */
public interface DocumentSnapshotProvider {

	List<DocumentView> snapshot(UUID userId);
}
