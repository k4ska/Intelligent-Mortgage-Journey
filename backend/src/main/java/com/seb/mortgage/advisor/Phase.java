package com.seb.mortgage.advisor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the conversation is (#73, {@code docs/architecture.md} §3 layer 1), worked out from
 * the stored data by {@link Phases}, never by the model. Each phase gives the advisor a
 * short focus and decides which tools it gets. Tools unlock cumulatively, so the
 * conversation can still jump around (a kv.ee link in the first message, for example).
 */
public enum Phase {

	GETTING_TO_KNOW_YOU("getting to know you",
			"Get to know the user: ask simple questions, one at a time, about their income, savings, whether "
					+ "they buy alone or with someone, and other loans.",
			List.of("updateProfile", "lookupListing", "updateProperty", "explainTerm")),
	PROPERTY("property",
			"Ask what home they have in mind (a kv.ee link, or its type, price and location), or tell them "
					+ "what price range they could afford.",
			List.of("checkAffordability", "suggestAffordableRange")),
	AFFORDABILITY("affordability",
			"Check whether they can afford the home they have in mind and explain the result in plain words.",
			List.of()),
	DOCUMENTS("documents",
			"Documents are optional in this prototype and can be skipped before applying.",
			List.of("confirmDocument")),
	READY("ready",
			"The profile and property facts needed for this prototype are confirmed. Tell the user they're "
					+ "ready and offer to send their application to the banks.",
			List.of("showBankPicker", "confirmDocument")),
	AT_THE_BANKS("at the banks",
			"Their application is with the banks. Help them understand, compare and answer the banks' emails.",
			List.of());

	private final String label;
	private final String focus;
	private final List<String> added;

	Phase(String label, String focus, List<String> added) {
		this.label = label;
		this.focus = focus;
		this.added = added;
	}

	public String label() {
		return label;
	}

	/** What the advisor should concentrate on in this phase, added to its prompt. */
	public String focus() {
		return focus;
	}

	/** The advisor's tools in this phase: this phase's own and every earlier phase's. */
	public Set<String> tools() {
		Set<String> tools = new LinkedHashSet<>();
		for (Phase phase : values()) {
			tools.addAll(phase.added);
			if (phase == this) {
				break;
			}
		}
		return tools;
	}
}
