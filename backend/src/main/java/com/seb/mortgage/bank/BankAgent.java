package com.seb.mortgage.bank;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One bank's side of an email thread, configured by the bank it's called with. It sees only
 * the frozen application snapshot and that bank's rules, never the advisor conversation.
 *
 * <p>Skeleton: the first reply is canned. The LLM version comes with the offer issues.
 */
@Component
public class BankAgent {

	public record Draft(String type, String subject, String body) {}

	private static final String SUBJECT = "Your mortgage application is received";

	private final ObjectMapper mapper;

	public BankAgent(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public Draft initialReply(BankCatalogService.Bank bank, String snapshotJson) {
		String need = hasValue(snapshotJson, "netIncome")
				? "the property you want to buy and the loan amount you need"
				: "your monthly net income, the property you want to buy and the loan amount you need";
		return new Draft("question", SUBJECT,
				bank.greeting() + " Before we can assess it, please send us " + need + ".");
	}

	/** Whether the snapshot (a list of profile fields) has a non-blank value for this key. */
	private boolean hasValue(String snapshotJson, String key) {
		try {
			for (JsonNode field : mapper.readTree(snapshotJson)) {
				JsonNode value = field.path("value");
				if (key.equals(field.path("key").asString()) && value.isString() && !value.asString().isBlank()) {
					return true;
				}
			}
			return false;
		} catch (JacksonException e) {
			return false;
		}
	}
}
