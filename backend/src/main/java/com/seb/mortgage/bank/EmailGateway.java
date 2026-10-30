package com.seb.mortgage.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the two sides meet (docs/architecture.md §1). The bank side writes its emails here;
 * the advisor reads the user's threads here, and never the other way round.
 */
public interface EmailGateway {
	EmailThread createThread(UUID applicationId, UUID userId, String bankId);
	Email addBankEmail(UUID threadId, BankAgent.Draft draft, OfferCalculator.Offer offer);
	Email addUserEmail(UUID threadId, String subject, String body);
	default Email addBankEmail(UUID threadId, BankAgent.Draft draft) {
		return addBankEmail(threadId, draft, null);
	}

	/** The user's threads, oldest first. */
	List<EmailThread> threads(UUID userId);

	/** The user's thread, or empty if it isn't theirs. */
	Optional<EmailThread> thread(UUID userId, UUID threadId);

	/** A thread's emails, oldest first. */
	List<Email> emails(UUID threadId);
}
