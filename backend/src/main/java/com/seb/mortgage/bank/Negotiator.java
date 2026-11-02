package com.seb.mortgage.bank;

import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Whether a reply to an offer asks for a better one. With an API key the
 * {@link NegotiationAgent} reads it; without one, or when it fails, a few keywords do.
 */
@Component
public class Negotiator {

	private static final Logger log = LoggerFactory.getLogger(Negotiator.class);

	private static final Pattern ASKS = Pattern.compile(
			"\\b(lower|better|reduce|discount|cheaper|negotiat\\w*|match|beat|margin|improve)\\b");
	private static final Pattern OTHER_BANK = Pattern.compile("\\b(another|other) (bank|offer)\\b");

	private final ObjectProvider<NegotiationAgent> agent;
	private final BankCatalogService banks;

	public Negotiator(ObjectProvider<NegotiationAgent> agent, BankCatalogService banks) {
		this.agent = agent;
		this.banks = banks;
	}

	public NegotiationAgent.Reading read(BankCatalogService.Bank bank, String reply) {
		NegotiationAgent reader = agent.getIfAvailable();
		if (reader != null) {
			try {
				return reader.read(bank.name(), reply);
			} catch (RuntimeException e) {
				log.warn("Negotiation reader failed, falling back to keywords: {}", e.toString());
			}
		}
		return byKeywords(bank, reply);
	}

	NegotiationAgent.Reading byKeywords(BankCatalogService.Bank bank, String reply) {
		String text = reply.toLowerCase(Locale.ROOT);
		boolean asks = ASKS.matcher(text).find();
		boolean otherBank = OTHER_BANK.matcher(text).find() || banks.banks().stream()
				.filter(other -> !other.id().equals(bank.id()))
				.anyMatch(other -> Pattern.compile("\\b" + Pattern.quote(other.name().toLowerCase(Locale.ROOT)) + "\\b")
						.matcher(text).find());
		return new NegotiationAgent.Reading(asks, asks && otherBank);
	}
}
