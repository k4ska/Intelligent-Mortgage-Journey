package com.seb.mortgage.documents;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;

import tools.jackson.databind.ObjectMapper;

/**
 * "Reads" an uploaded document (#72) and saves its values to the profile as uncertain,
 * from the document, until the user confirms them. A value the user already gave is read
 * exactly as they gave it; only the others are made up, by the {@link DocumentReaderAgent}.
 * Without an LLM nothing is read at all, so nothing is invented.
 */
@Service
public class DocumentReader {

	/** No made-up amount is larger than this; anything else means the model went wrong. */
	private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(1_000_000);

	private static final Logger log = LoggerFactory.getLogger(DocumentReader.class);

	private final ObjectProvider<DocumentReaderAgent> agent;
	private final DocumentService documents;
	private final ProfileService profile;
	private final ObjectMapper mapper;

	public DocumentReader(ObjectProvider<DocumentReaderAgent> agent, DocumentService documents, ProfileService profile,
			ObjectMapper mapper) {
		this.agent = agent;
		this.documents = documents;
		this.profile = profile;
		this.mapper = mapper;
	}

	/**
	 * @param document the document as it is now
	 * @param fields   the profile fields saved from it, uncertain
	 * @param read     false when nothing could be read (no LLM, or it failed); then nothing changed
	 */
	public record Reading(DocumentView document, List<ProfileFieldView> fields, boolean read) {}

	/**
	 * @param userMessages what the user wrote in the chat, for clues like their job
	 * @throws java.util.NoSuchElementException if the user has no such document
	 */
	public Reading read(UUID userId, UUID documentId, List<String> userMessages) {
		DocumentView document = documents.find(userId, documentId).orElseThrow();
		DocumentReaderAgent reader = agent.getIfAvailable();
		if (reader == null) {
			return new Reading(document, List.of(), false);
		}
		List<ProfileKey> keys = document.type().reads();
		if (keys.isEmpty()) {
			return new Reading(documents.recordReading(userId, documentId, Map.of()), List.of(), true);
		}

		Map<ProfileKey, String> values = new EnumMap<>(ProfileKey.class);
		List<ProfileFieldView> known = profile.fields(userId);
		for (ProfileKey key : keys) {
			known.stream().filter(field -> field.key().equals(key.key())).findFirst()
					.ifPresent(field -> values.put(key, field.value()));
		}
		List<ProfileKey> unknown = keys.stream().filter(key -> !values.containsKey(key)).toList();
		if (!unknown.isEmpty()) {
			values.putAll(madeUp(reader, document.type(), unknown, known, userMessages));
		}
		if (values.isEmpty()) {
			return new Reading(document, List.of(), false);
		}

		List<ProfileFieldView> saved = new ArrayList<>();
		Map<String, String> read = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			saved.add(profile.saveFromDocument(userId, key, value, document.type().name()));
			read.put(key.key(), value);
		});
		return new Reading(documents.recordReading(userId, documentId, read), saved, true);
	}

	private Map<ProfileKey, String> madeUp(DocumentReaderAgent reader, DocumentType type, List<ProfileKey> keys,
			List<ProfileFieldView> known, List<String> userMessages) {
		Map<String, String> answer;
		try {
			answer = reader.read(type, keys, mapper.writeValueAsString(known), userMessages);
		} catch (RuntimeException e) {
			log.warn("The document reader failed for a {}; nothing is read from it", type, e);
			return Map.of();
		}
		Map<ProfileKey, String> values = new EnumMap<>(ProfileKey.class);
		for (ProfileKey key : keys) {
			String raw = answer.get(key.key());
			try {
				String value = key.normalize(raw);
				if (plausible(key, value)) {
					values.put(key, value);
				} else {
					log.warn("The document reader gave an implausible {}={}; it is not saved", key.key(), value);
				}
			} catch (IllegalArgumentException e) {
				log.warn("The document reader gave no usable {} ({}); it is not saved", key.key(), raw);
			}
		}
		return values;
	}

	private static boolean plausible(ProfileKey key, String value) {
		if (key.type() != ProfileKey.Type.AMOUNT) {
			return true;
		}
		BigDecimal amount = new BigDecimal(value);
		boolean income = key == ProfileKey.NET_INCOME || key == ProfileKey.CO_APPLICANT_NET_INCOME;
		return amount.compareTo(MAX_AMOUNT) <= 0 && (!income || amount.signum() > 0);
	}
}
