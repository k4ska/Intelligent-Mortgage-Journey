package com.seb.mortgage.documents;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;

import com.seb.mortgage.agent.LlmSpecialistAgent;
import com.seb.mortgage.profile.ProfileKey;

/**
 * Plays the document reader ({@code docs/architecture.md} §2): we never read the uploaded
 * file, so this makes up the values a real document of that kind would show, fitting what
 * the user has said. It is only asked for values the user hasn't given yet; the
 * {@link DocumentReader} copies the ones they have, so nothing it says can contradict them.
 */
public class DocumentReaderAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You play the document reader in a prototype mortgage app for people in Estonia. Users
			upload any file as a stand-in for a real document, and nobody reads the file. You make
			up the values a real document of that kind would show, so that the rest of the
			journey feels real.
			- The values must fit everything the user has told you: their job, their household,
			  their other figures. Never contradict any of it.
			- Make them believable for a household in Estonia today. Euro amounts are whole
			  numbers like 2350, with no currency sign or separators.
			- Give exactly the keys you are asked for, nothing else.""";

	/** What each key means, for the prompt. */
	private static final Map<ProfileKey, String> MEANINGS = Map.of(
			ProfileKey.NET_INCOME, "the user's monthly net income after tax, in euros",
			ProfileKey.CO_APPLICANT_NET_INCOME, "the co-applicant's monthly net income after tax, in euros",
			ProfileKey.SAVINGS, "the savings available for the down payment, in euros",
			ProfileKey.MONTHLY_DEBT_PAYMENTS, "monthly payments on other loans and leases, in euros (0 if none)",
			ProfileKey.MONTHLY_LIVING_COSTS, "what the household spends a month on living, without loan payments, in euros");

	record Value(String key, String value) {}

	record Values(List<Value> values) {}

	public DocumentReaderAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/**
	 * @param profileJson  the profile facts so far
	 * @param userMessages what the user wrote in the chat, for clues like their job
	 * @return the made-up values by key, as the model wrote them (the caller checks them)
	 */
	Map<String, String> read(DocumentType type, List<ProfileKey> keys, String profileJson, List<String> userMessages) {
		String wanted = keys.stream()
				.map(key -> "- " + key.key() + ": " + MEANINGS.getOrDefault(key, key.key()))
				.collect(Collectors.joining("\n"));
		String said = userMessages.isEmpty() ? "(nothing yet)"
				: userMessages.stream().map(text -> "- " + text).collect(Collectors.joining("\n"));
		Values values = chatClient.prompt()
				.user(u -> u.text("""
						Document: {document}

						Values to read from it:
						{wanted}

						The user's profile so far (JSON): {profile}

						What the user wrote in the chat:
						{said}
						""")
						.param("document", type.label())
						.param("wanted", wanted)
						.param("profile", profileJson)
						.param("said", said))
				.call()
				.entity(Values.class);
		if (values == null || values.values() == null) {
			return Map.of();
		}
		return values.values().stream()
				.filter(value -> value.key() != null && value.value() != null)
				.collect(Collectors.toMap(Value::key, Value::value, (first, second) -> first));
	}
}
