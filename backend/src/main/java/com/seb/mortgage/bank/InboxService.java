package com.seb.mortgage.bank;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.seb.mortgage.auth.CurrentUser;

@Service
public class InboxService {
	private static final String BANK_REPLY = "Thank you for your reply. We have received it and will send you any additional information as soon as possible.";

	private final CurrentUser currentUser;
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final EmailGateway gateway;

	public InboxService(CurrentUser currentUser, EmailThreadRepository threads, EmailRepository emails,
			EmailGateway gateway) {
		this.currentUser = currentUser;
		this.threads = threads;
		this.emails = emails;
		this.gateway = gateway;
	}

	@Transactional
	public void reply(UUID threadId, String body) {
		EmailThread thread = owned(threadId);
		if (thread.getStatus() == EmailThread.Status.ACCEPTED || thread.getStatus() == EmailThread.Status.DECLINED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This thread cannot receive a reply");
		}
		Email last = emails.findByThreadIdOrderByCreatedAtAsc(threadId).stream().reduce((a, b) -> b).orElse(null);
		String subject = last == null ? "Mortgage application" : "Re: " + last.getSubject();
		gateway.addUserEmail(threadId, subject, body);
		if (thread.getStatus() != EmailThread.Status.CLOSED) {
			gateway.addBankEmail(threadId,
					new BankAgent.Draft("confirmation", "We will be in touch soon", BANK_REPLY), null);
		}
	}

	@Transactional
	public void accept(UUID threadId, UUID emailId) {
		EmailThread thread = owned(threadId);
		if (thread.getStatus() == EmailThread.Status.ACCEPTED || thread.getStatus() == EmailThread.Status.CLOSED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This application was already closed");
		}
		Email email = emails.findById(emailId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Offer email was not found"));
		if (!threadId.equals(email.getThreadId()) || email.getFrom() != Email.Sender.BANK) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a bank offer email");
		}
		if (!"offer".equals(email.getType()) || email.getOfferJson() == null
				|| thread.getStatus() != EmailThread.Status.OFFER) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This thread has no open offer");
		}
		thread.accept();
		gateway.addBankEmail(threadId, new BankAgent.Draft("confirmation", "Mortgage application accepted",
				"We have received your acceptance. We will send you the next steps and any additional information as soon as possible."), null);
		threads.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
				.filter(other -> !other.getId().equals(threadId))
				.forEach(EmailThread::close);
	}

	private EmailThread owned(UUID threadId) {
		return threads.findByIdAndUserId(threadId, currentUser.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread was not found"));
	}
}
