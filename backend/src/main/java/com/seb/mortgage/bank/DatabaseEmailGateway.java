package com.seb.mortgage.bank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DatabaseEmailGateway implements EmailGateway {
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final ObjectMapper mapper;
	public DatabaseEmailGateway(EmailThreadRepository threads, EmailRepository emails, ObjectMapper mapper) {
		this.threads = threads; this.emails = emails; this.mapper = mapper;
	}
	@Override @Transactional
	public EmailThread createThread(UUID applicationId, UUID userId, String bankId) {
		return threads.save(new EmailThread(applicationId, userId, bankId));
	}
	@Override @Transactional
	public Email addBankEmail(UUID threadId, BankAgent.Draft draft, OfferCalculator.Offer offer) {
		String offerJson = null;
		if (offer != null) {
			try {
				offerJson = mapper.writeValueAsString(offer);
			} catch (JacksonException e) {
				throw new IllegalStateException("Could not store the bank offer", e);
			}
		}
		Email email = emails.save(new Email(threadId, Email.Sender.BANK, draft.type(), draft.subject(),
				draft.body(), offerJson));
		threads.findById(threadId).ifPresent(thread -> {
			thread.markUnread();
			if ("offer".equals(draft.type())) {
				thread.setStatus(EmailThread.Status.OFFER);
			} else if ("decline".equals(draft.type())) {
				thread.setStatus(EmailThread.Status.DECLINED);
			} else if ("question".equals(draft.type())) {
				thread.setStatus(EmailThread.Status.OPEN);
			}
		});
		return email;
	}

	@Override @Transactional
	public Email addUserEmail(UUID threadId, String subject, String body) {
		return emails.save(new Email(threadId, Email.Sender.USER, "reply", subject, body));
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
