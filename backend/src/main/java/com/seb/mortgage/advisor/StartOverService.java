package com.seb.mortgage.advisor;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seb.mortgage.bank.ApplicationRepository;
import com.seb.mortgage.bank.EmailRepository;
import com.seb.mortgage.bank.EmailThread;
import com.seb.mortgage.bank.EmailThreadRepository;
import com.seb.mortgage.conversation.ConversationService;
import com.seb.mortgage.documents.DocumentRepository;
import com.seb.mortgage.profile.ProfileFieldRepository;
import com.seb.mortgage.property.PropertyRepository;

/**
 * "Start over" (#70): deletes everything below the user's account, so they begin again from
 * the greeting ({@code docs/architecture.md} §4). The account and its login stay.
 */
@Service
public class StartOverService {

	private final ConversationService conversations;
	private final ProfileFieldRepository profileFields;
	private final PropertyRepository properties;
	private final ApplicationRepository applications;
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final DocumentRepository documents;

	public StartOverService(ConversationService conversations, ProfileFieldRepository profileFields,
			PropertyRepository properties, ApplicationRepository applications, EmailThreadRepository threads,
			EmailRepository emails, DocumentRepository documents) {
		this.conversations = conversations;
		this.profileFields = profileFields;
		this.properties = properties;
		this.applications = applications;
		this.threads = threads;
		this.emails = emails;
		this.documents = documents;
	}

	/** All or nothing: a failure part way leaves the user's data as it was. */
	@Transactional
	public void wipe(UUID userId) {
		List<UUID> threadIds = threads.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(EmailThread::getId).toList();
		if (!threadIds.isEmpty()) {
			emails.deleteByThreadIdIn(threadIds);
		}
		threads.deleteByUserId(userId);
		applications.deleteByUserId(userId);
		documents.deleteByUserId(userId);
		properties.deleteByUserId(userId);
		profileFields.deleteByUserId(userId);
		conversations.delete(userId);
	}
}
