package com.seb.mortgage.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.seb.mortgage.auth.CurrentUser;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class InboxService {
	/** Margins are rounded to two decimals; closer than this is the same margin. */
	private static final double MARGIN_EPSILON = 0.001;
	private static final String BANK_REPLY = "Thank you for your reply. We have received it and will send you any additional information as soon as possible.";

	private final CurrentUser currentUser;
	private final EmailThreadRepository threads;
	private final EmailRepository emails;
	private final EmailGateway gateway;
	private final BankCatalogService banks;
	private final OfferCalculator calculator;
	private final BankAgent agent;
	private final Negotiator negotiator;
	private final ObjectMapper mapper;

	public InboxService(CurrentUser currentUser, EmailThreadRepository threads, EmailRepository emails,
			EmailGateway gateway, BankCatalogService banks, OfferCalculator calculator, BankAgent agent,
			Negotiator negotiator, ObjectMapper mapper) {
		this.currentUser = currentUser;
		this.threads = threads;
		this.emails = emails;
		this.gateway = gateway;
		this.banks = banks;
		this.calculator = calculator;
		this.agent = agent;
		this.negotiator = negotiator;
		this.mapper = mapper;
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
		if (thread.getStatus() == EmailThread.Status.CLOSED) {
			return;
		}
		if (thread.getStatus() == EmailThread.Status.OFFER && negotiate(thread, body)) {
			return;
		}
		gateway.addBankEmail(threadId,
				new BankAgent.Draft("confirmation", "We will be in touch soon", BANK_REPLY), null);
	}

	/**
	 * Negotiation, margin only (docs/architecture.md §6). Each time the user asks, the bank
	 * lowers its margin one step, as far as the calculator allows this round; another bank's
	 * offer gets its lowest margin straight away. At the lowest margin it is the final offer,
	 * and asking again gets a polite no.
	 *
	 * @return false when the reply doesn't ask for a better offer, so it gets the usual answer
	 */
	private boolean negotiate(EmailThread thread, String body) {
		List<Email> offers = offerEmails(thread.getId());
		Optional<OfferCalculator.Offer> current = offers.isEmpty() ? Optional.empty() : offer(offers.getLast());
		if (current.isEmpty()) {
			return false;
		}
		BankCatalogService.Bank bank = banks.find(thread.getBankId());
		NegotiationAgent.Reading reading = negotiator.read(bank, body);
		if (!reading.asksForBetterOffer()) {
			return false;
		}
		// The first offer is round 0, so the next round is the number of offers so far.
		int round = Math.min(offers.size(), bank.maxNegotiationRounds());
		double lowest = calculator.lowestMargin(bank, bank.maxNegotiationRounds());
		double margin = reading.mentionsCompetingOffer() ? lowest : calculator.lowestMargin(bank, round);
		if (margin >= current.get().marginPct() - MARGIN_EPSILON) {
			gateway.addBankEmail(thread.getId(), agent.noBetterOffer(bank), null);
			return true;
		}
		OfferCalculator.Offer better = calculator.withMargin(current.get(), margin);
		gateway.addBankEmail(thread.getId(), agent.counterOffer(bank, better, margin <= lowest + MARGIN_EPSILON),
				better);
		return true;
	}

	/** The thread's offer emails, oldest first; the last one is the offer that stands. */
	private List<Email> offerEmails(UUID threadId) {
		return emails.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
				.filter(email -> email.getFrom() == Email.Sender.BANK && "offer".equals(email.getType())
						&& email.getOfferJson() != null)
				.toList();
	}

	private Optional<OfferCalculator.Offer> offer(Email email) {
		try {
			return Optional.of(mapper.readValue(email.getOfferJson(), OfferCalculator.Offer.class));
		} catch (JacksonException e) {
			return Optional.empty();
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
		List<Email> offers = offerEmails(threadId);
		if (!offers.getLast().getId().equals(emailId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer offer replaced this one");
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
