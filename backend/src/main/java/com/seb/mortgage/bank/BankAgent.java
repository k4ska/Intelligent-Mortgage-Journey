package com.seb.mortgage.bank;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BankAgent {
	public record Draft(String type, String subject, String body) {}

	private final ObjectMapper mapper;
	public BankAgent(ObjectMapper mapper) { this.mapper = mapper; }

	public Draft initialReply(BankCatalogService.Bank bank, String snapshotJson) {
		try {
			JsonNode snapshot = mapper.readTree(snapshotJson);
			boolean hasIncome = snapshot.toString().contains("netIncome");
			String need = hasIncome ? "the property and requested loan amount" : "your monthly net income, the property and requested loan amount";
			return new Draft("question", "Your mortgage application is received",
					"Thank you for contacting " + bank.name() + ". " +
					"Our " + bank.tone() + " team needs " + need + " before we can assess the application. " +
					"Please reply with those details.");
		} catch (JacksonException e) {
			return new Draft("question", "Your mortgage application is received",
					"Thank you for contacting " + bank.name() + ". Please reply with the property and requested loan amount.");
		}
	}
}