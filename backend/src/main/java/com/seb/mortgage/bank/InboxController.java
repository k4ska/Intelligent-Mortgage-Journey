package com.seb.mortgage.bank;

import com.seb.mortgage.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {
	public record LastEmail(String subject, String preview, java.time.Instant createdAt) {}
	public record ThreadSummary(UUID threadId, BankController.BankOption bank, String status, boolean unread, LastEmail lastEmail) {}
	public record EmailView(UUID id, Email.Sender from, String type, String subject, String body, Object offer, java.time.Instant createdAt) {}
	public record ThreadDetail(UUID threadId, BankController.BankOption bank, String status, List<EmailView> emails) {}

	private final CurrentUser currentUser;
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final BankCatalogService banks;

	public InboxController(CurrentUser currentUser, EmailThreadRepository threads, EmailRepository emails, BankCatalogService banks) {
		this.currentUser = currentUser; this.threads = threads; this.emails = emails; this.banks = banks;
	}

	@GetMapping
	public List<ThreadSummary> list() {
		return threads.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream().map(thread -> {
			List<Email> items = emails.findByThreadIdOrderByCreatedAtAsc(thread.getId());
			Email last = items.isEmpty() ? null : items.get(items.size() - 1);
			LastEmail preview = last == null ? null : new LastEmail(last.getSubject(), last.getBody(), last.getCreatedAt());
			return new ThreadSummary(thread.getId(), bank(thread.getBankId()), thread.getStatus().name(), thread.isUnread(), preview);
		}).toList();
	}

	@GetMapping("/{threadId}")
	public ThreadDetail detail(@PathVariable UUID threadId) {
		EmailThread thread = threads.findByIdAndUserId(threadId, currentUser.id()).orElseThrow(NotFoundException::new);
		thread.markRead(); threads.save(thread);
		return new ThreadDetail(thread.getId(), bank(thread.getBankId()), thread.getStatus().name(),
				emails.findByThreadIdOrderByCreatedAtAsc(threadId).stream().map(email ->
					new EmailView(email.getId(), email.getFrom(), email.getType(), email.getSubject(), email.getBody(), null, email.getCreatedAt())).toList());
	}

	private BankController.BankOption bank(String id) {
		BankCatalogService.Bank bank = banks.find(id);
		return new BankController.BankOption(bank.id(), bank.name());
	}
	@ResponseStatus(HttpStatus.NOT_FOUND)
	private static class NotFoundException extends RuntimeException {}
}