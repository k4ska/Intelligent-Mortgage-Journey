package com.seb.mortgage.bank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseEmailGateway implements EmailGateway {
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	public DatabaseEmailGateway(EmailThreadRepository threads, EmailRepository emails) {
		this.threads = threads; this.emails = emails;
	}
	@Override @Transactional
	public EmailThread createThread(UUID applicationId, UUID userId, String bankId) {
		return threads.save(new EmailThread(applicationId, userId, bankId));
	}
	@Override @Transactional
	public Email addBankEmail(UUID threadId, BankAgent.Draft draft) {
		return emails.save(new Email(threadId, Email.Sender.BANK, draft.type(), draft.subject(), draft.body()));
	}
	@Override @Transactional(readOnly = true)
	public List<EmailThread> threads(UUID userId) {
		return threads.findByUserIdOrderByCreatedAtAsc(userId);
	}
	@Override @Transactional(readOnly = true)
	public Optional<EmailThread> thread(UUID userId, UUID threadId) {
		return threads.findByIdAndUserId(threadId, userId);
	}
	@Override @Transactional(readOnly = true)
	public List<Email> emails(UUID threadId) {
		return emails.findByThreadIdOrderByCreatedAtAsc(threadId);
	}
}
