package com.seb.mortgage.bank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
