package com.seb.mortgage.documents;

import java.util.Map;
import java.util.UUID;

/**
 * A document as the documents panel, the advisor and the {@code document_status} event see
 * it. {@code id} is null for a document nobody has asked for or uploaded yet.
 *
 * @param values what the document reader read from it, by profile key; empty until read
 */
public record DocumentView(UUID id, DocumentType type, String label, Document.Status status, String whereToGet,
		String fileName, Map<String, String> values) {

	static DocumentView notNeededYet(DocumentType type) {
		return new DocumentView(null, type, type.label(), Document.Status.NOT_NEEDED_YET, type.whereToGet(), null,
				Map.of());
	}
}
