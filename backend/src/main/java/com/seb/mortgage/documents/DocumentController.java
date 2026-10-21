package com.seb.mortgage.documents;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seb.mortgage.auth.CurrentUser;

/**
 * The documents panel (#72). Any file is accepted and only its name is kept; after an
 * upload the frontend sends the {@code DOCUMENT_UPLOADED} trigger to the chat, and the
 * advisor's turn reads it.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	public record ErrorView(String error) {}

	private final DocumentService documents;
	private final CurrentUser currentUser;

	public DocumentController(DocumentService documents, CurrentUser currentUser) {
		this.documents = documents;
		this.currentUser = currentUser;
	}

	/** Every document that applies to the user, with its status and where to get it. */
	@GetMapping
	public List<DocumentView> checklist() {
		return documents.checklist(currentUser.id());
	}

	@PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DocumentView upload(@PathVariable String type, @RequestParam("file") MultipartFile file) {
		DocumentType documentType = DocumentType.parse(type)
				.orElseThrow(() -> new IllegalArgumentException("Unknown document type: " + type));
		return documents.upload(currentUser.id(), documentType, file.getOriginalFilename());
	}

	/** An unknown type, or a co-applicant document without a co-applicant. */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorView invalidUpload(IllegalArgumentException e) {
		return new ErrorView(e.getMessage());
	}

	/** A document the advisor hasn't asked for yet. */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorView notAskedFor(IllegalStateException e) {
		return new ErrorView(e.getMessage());
	}
}
