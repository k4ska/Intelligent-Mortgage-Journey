package com.seb.mortgage.bank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seb.mortgage.auth.CurrentUser;

/** The user's read-only inbox: one thread per bank they applied to. */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

	static final int PREVIEW_LENGTH = 140;

	public record LastEmail(String subject, String preview, Instant createdAt) {}

	public record ThreadSummary(UUID threadId, BankController.BankOption bank, String status, boolean unread,
			LastEmail lastEmail) {}

	public record EmailView(UUID id, Email.Sender from, String type, String subject, String body, Object offer,
			Instant createdAt) {}

	public record ThreadDetail(UUID threadId, BankController.BankOption bank, String status, List<EmailView> emails) {}

	private final CurrentUser currentUser;
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final BankCatalogService banks;

	public InboxController(CurrentUser currentUser, EmailThreadRepository threads, EmailRepository emails,
			BankCatalogService banks) {
		this.currentUser = currentUser;
		this.threads = threads;
		this.emails = emails;
		this.banks = banks;
	}

	@GetMapping
	public List<ThreadSummary> list() {
		return threads.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream().map(thread -> {
			List<Email> items = emails.findByThreadIdOrderByCreatedAtAsc(thread.getId());
			Email last = items.isEmpty() ? null : items.get(items.size() - 1);
			LastEmail preview = last == null ? null
					: new LastEmail(last.getSubject(), preview(last.getBody()), last.getCreatedAt());
			return new ThreadSummary(thread.getId(), bank(thread.getBankId()), thread.getStatus().name(),
					thread.isUnread(), preview);
		}).toList();
	}

	/** Opening a thread marks it as read. */
	@GetMapping("/{threadId}")
	@Transactional
	public ThreadDetail detail(@PathVariable UUID threadId) {
		EmailThread thread = threads.findByIdAndUserId(threadId, currentUser.id()).orElseThrow(NotFoundException::new);
		thread.markRead();
		return new ThreadDetail(thread.getId(), bank(thread.getBankId()), thread.getStatus().name(),
				emails.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
						.map(email -> new EmailView(email.getId(), email.getFrom(), email.getType(),
								email.getSubject(), email.getBody(), null, email.getCreatedAt()))
						.toList());
	}

	static String preview(String body) {
		String flat = body.replaceAll("\\s+", " ").strip();
		return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH - 1).stripTrailing() + "…";
	}

	private BankController.BankOption bank(String id) {
		BankCatalogService.Bank bank = banks.find(id);
		return new BankController.BankOption(bank.id(), bank.name());
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	private static class NotFoundException extends RuntimeException {}
}
