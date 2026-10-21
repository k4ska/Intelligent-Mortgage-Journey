package com.seb.mortgage.bank;

import org.springframework.stereotype.Component;


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

	private final ApplicationSnapshots snapshots;

	public BankAgent(ApplicationSnapshots snapshots) {
		this.snapshots = snapshots;
	}

	public Draft initialReply(BankCatalogService.Bank bank, String snapshotJson) {
		String need = hasValue(snapshotJson, "netIncome")
				? "the property you want to buy and the loan amount you need"
				: "your monthly net income, the property you want to buy and the loan amount you need";
		return new Draft("question", SUBJECT,
				bank.greeting() + " Before we can assess it, please send us " + need + ".");
	}

	/** Whether the snapshot's profile fields have a non-blank value for this key. */
	private boolean hasValue(String snapshotJson, String key) {
		try {
			return snapshots.read(snapshotJson).fields().stream()
					.anyMatch(field -> key.equals(field.key()) && field.value() != null && !field.value().isBlank());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
