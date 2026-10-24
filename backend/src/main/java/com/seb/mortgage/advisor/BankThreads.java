package com.seb.mortgage.advisor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.bank.Email;
import com.seb.mortgage.bank.EmailGateway;
import com.seb.mortgage.bank.EmailThread;

/**
 * The user's bank emails as the advisor sees them (#80). The advisor reads them through the
 * email gateway; the bank side never reads the conversation (docs/architecture.md §1).
 */
@Service
public class BankThreads {

	/** How many of a thread's latest emails the advisor sees, to keep prompts bounded. */
	static final int EMAILS_PER_THREAD = 6;

	public record BankEmail(String from, String type, String subject, String body) {}

	public record BankThread(UUID threadId, String bank, EmailThread.Status status, List<BankEmail> emails) {}

	private final EmailGateway gateway;
	private final BankCatalogService banks;

	public BankThreads(EmailGateway gateway, BankCatalogService banks) {
		this.gateway = gateway;
		this.banks = banks;
	}

	/** The user's threads, oldest first; empty before they apply. */
	public List<BankThread> all(UUID userId) {
		return gateway.threads(userId).stream().map(this::view).toList();
	}

	/** The user's thread, or empty if it isn't theirs. */
	public Optional<BankThread> find(UUID userId, UUID threadId) {
		return gateway.thread(userId, threadId).map(this::view);
	}

	/** The threads one of the user's applications opened; empty if the application isn't theirs. */
	public List<BankThread> ofApplication(UUID userId, UUID applicationId) {
		return gateway.threads(userId).stream()
				.filter(thread -> thread.getApplicationId().equals(applicationId))
				.map(this::view)
				.toList();
	}

	private BankThread view(EmailThread thread) {
		List<Email> emails = gateway.emails(thread.getId());
		return new BankThread(thread.getId(), banks.find(thread.getBankId()).name(), thread.getStatus(),
				emails.subList(Math.max(0, emails.size() - EMAILS_PER_THREAD), emails.size()).stream()
						.map(email -> new BankEmail(email.getFrom().name(), email.getType(), email.getSubject(),
								email.getBody()))
						.toList());
	}
}
