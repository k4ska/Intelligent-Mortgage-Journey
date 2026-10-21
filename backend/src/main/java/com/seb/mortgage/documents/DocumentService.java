package com.seb.mortgage.documents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The user's documents (#72): which ones a bank will need, which are uploaded, and whether
 * the user has confirmed what was read from them. A document is confirmed once none of the
 * values read from it is uncertain any more, because the user said "yes" or corrected them.
 */
@Service
public class DocumentService {

	private static final int MAX_FILE_NAME = 255;

	private final DocumentRepository documents;
	private final ProfileService profile;
	private final ObjectMapper mapper;

	public DocumentService(DocumentRepository documents, ProfileService profile, ObjectMapper mapper) {
		this.documents = documents;
		this.profile = profile;
		this.mapper = mapper;
	}

	/**
	 * Every document that applies to the user, in {@link DocumentType} order: co-applicant
	 * documents only when there is a co-applicant (or one was already uploaded).
	 */
	@Transactional(readOnly = true)
	public List<DocumentView> checklist(UUID userId) {
		Map<DocumentType, Document> stored = new EnumMap<>(DocumentType.class);
		documents.findByUserId(userId).forEach(document -> stored.put(document.getType(), document));
		boolean coApplicant = hasCoApplicant(userId);
		return Arrays.stream(DocumentType.values())
				.filter(type -> !type.coApplicantOnly() || coApplicant || stored.containsKey(type))
				.map(type -> stored.containsKey(type) ? view(stored.get(type)) : DocumentView.notNeededYet(type))
				.toList();
	}

	/**
	 * Marks a document as needed; one that is already needed, uploaded or confirmed stays as it is.
	 *
	 * @throws IllegalArgumentException for a co-applicant document when there is no co-applicant
	 */
	@Transactional
	public DocumentView request(UUID userId, DocumentType type) {
		checkApplies(userId, type);
		return view(documents.findByUserIdAndType(userId, type)
				.orElseGet(() -> documents.save(new Document(userId, type))));
	}

	/**
	 * Records an uploaded file (only its name); a new file replaces an earlier one. Only a
	 * document the advisor asked for can be uploaded (#73): the advisor confirms what is read
	 * from it in the documents phase, which starts with that request.
	 *
	 * @throws IllegalArgumentException for a co-applicant document when there is no co-applicant
	 * @throws IllegalStateException    when the advisor hasn't asked for this document yet
	 */
	@Transactional
	public DocumentView upload(UUID userId, DocumentType type, String fileName) {
		checkApplies(userId, type);
		Document document = documents.findByUserIdAndType(userId, type)
				.orElseThrow(() -> new IllegalStateException("Your advisor hasn't asked for this document yet"));
		document.uploaded(cleanFileName(fileName));
		return view(documents.save(document));
	}

	@Transactional(readOnly = true)
	public Optional<DocumentView> find(UUID userId, UUID documentId) {
		return documents.findByIdAndUserId(documentId, userId).map(this::view);
	}

	/**
	 * Keeps what the document reader read. A document it reads nothing from (an ID, for
	 * example) has nothing to confirm, so it is confirmed straight away.
	 */
	@Transactional
	public DocumentView recordReading(UUID userId, UUID documentId, Map<String, String> values) {
		Document document = documents.findByIdAndUserId(documentId, userId).orElseThrow();
		document.read(mapper.writeValueAsString(values));
		if (values.isEmpty()) {
			document.confirm();
		}
		return view(document);
	}

	/** What {@link #confirm} changed: the document and the profile fields it confirmed. */
	public record Confirmed(DocumentView document, List<ProfileFieldView> fields) {}

	/**
	 * The user said the values read from this document are right: they and the document
	 * become confirmed.
	 *
	 * @throws IllegalArgumentException when nothing was read from such a document yet
	 */
	@Transactional
	public Confirmed confirm(UUID userId, DocumentType type) {
		Document document = documents.findByUserIdAndType(userId, type)
				.filter(found -> found.getStatus() != Document.Status.NEEDED && found.getReadValues() != null)
				.orElseThrow(() -> new IllegalArgumentException("nothing has been read from a "
						+ type.name() + " yet, so there is nothing to confirm"));
		List<ProfileFieldView> fields = profile.confirmFromDocument(userId, type.name());
		document.confirm();
		return new Confirmed(view(document), fields);
	}

	/**
	 * Confirms the uploaded documents whose read values the user has now all confirmed or
	 * corrected (after the advisor saved what the user said); returns those documents.
	 */
	@Transactional
	public List<DocumentView> settle(UUID userId) {
		List<DocumentView> settled = new ArrayList<>();
		for (Document document : documents.findByUserId(userId)) {
			if (document.getStatus() == Document.Status.UPLOADED && !values(document).isEmpty()
					&& !profile.hasUncertainFrom(userId, document.getType().name())) {
				document.confirm();
				settled.add(view(document));
			}
		}
		return settled;
	}

	public boolean applies(UUID userId, DocumentType type) {
		return !type.coApplicantOnly() || hasCoApplicant(userId);
	}

	private void checkApplies(UUID userId, DocumentType type) {
		if (!applies(userId, type)) {
			throw new IllegalArgumentException(type.name() + " is only needed when buying with a co-applicant");
		}
	}

	private boolean hasCoApplicant(UUID userId) {
		return profile.fields(userId).stream()
				.anyMatch(field -> field.key().equals(ProfileKey.HAS_CO_APPLICANT.key()) && "yes".equals(field.value()));
	}

	/** Only the name itself: some browsers send the whole path. */
	private static String cleanFileName(String fileName) {
		String name = fileName == null ? "" : fileName.substring(Math.max(fileName.lastIndexOf('/'),
				fileName.lastIndexOf('\\')) + 1).strip();
		if (name.isEmpty()) {
			return "document";
		}
		return name.length() > MAX_FILE_NAME ? name.substring(0, MAX_FILE_NAME) : name;
	}

	private DocumentView view(Document document) {
		return new DocumentView(document.getId(), document.getType(), document.getType().label(),
				document.getStatus(), document.getType().whereToGet(), document.getFileName(), values(document));
	}

	private Map<String, String> values(Document document) {
		if (document.getReadValues() == null) {
			return Map.of();
		}
		return mapper.readValue(document.getReadValues(), new TypeReference<LinkedHashMap<String, String>>() {});
	}
}
