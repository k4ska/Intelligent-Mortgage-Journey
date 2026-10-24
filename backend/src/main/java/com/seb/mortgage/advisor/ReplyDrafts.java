package com.seb.mortgage.advisor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.seb.mortgage.agent.VerifierAgent;
import com.seb.mortgage.profile.ProfileService;

import tools.jackson.databind.ObjectMapper;

/**
 * Checks a reply the advisor drafted to a bank (#80) before it reaches the user's reply box:
 * every figure must come from the bank's emails, the profile or the user's own words (the
 * {@link NumberCheck}), then the verifier checks the tone. The user still edits and sends it.
 */
@Service
public class ReplyDrafts {

	/**
	 * @param body    the draft to show, possibly the verifier's calmer version; null when refused
	 * @param problem why the draft was refused, for the advisor to fix; null when it passed
	 */
	record Checked(String body, String problem) {
		boolean passed() {
			return problem == null;
		}
	}

	private static final Logger log = LoggerFactory.getLogger(ReplyDrafts.class);

	private final ObjectProvider<VerifierAgent> verifier;
	private final ProfileService profile;
	private final ObjectMapper objectMapper;

	public ReplyDrafts(ObjectProvider<VerifierAgent> verifier, ProfileService profile, ObjectMapper objectMapper) {
		this.verifier = verifier;
		this.profile = profile;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param thread the thread the draft answers
	 * @param said   everything the user has said in the conversation, this turn included
	 */
	Checked check(UUID userId, BankThreads.BankThread thread, String draft, List<String> said) {
		List<String> sources = new ArrayList<>(said);
		thread.emails().forEach(email -> sources.add(email.subject() + "\n" + email.body()));
		String profileJson = objectMapper.writeValueAsString(profile.fields(userId));
		sources.add(profileJson);
		NumberCheck.Result numbers = NumberCheck.check(draft, sources);
		if (!numbers.passed()) {
			return new Checked(null, "these figures appear in none of the bank's emails, the profile or the user's "
					+ "messages: " + String.join(", ", numbers.unsupported()));
		}

		VerifierAgent checker = verifier.getIfAvailable();
		if (checker == null) {
			return new Checked(draft, null);
		}
		Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("profile", profile.fields(userId));
		facts.put("bankEmails", thread.emails());
		facts.put("userMessages", said);
		VerifierAgent.Verdict verdict = checker.verify("""
				An email from a home buyer to %s, drafted by their mortgage advisor for them to edit and \
				send. It must be polite and calm, never pushy or threatening, make no promises for the \
				user, and use only figures from the facts.""".formatted(thread.bank()), draft,
				objectMapper.writeValueAsString(facts));
		if (!verdict.approved()) {
			log.info("Reply draft to {} rewritten by the verifier", thread.bank());
		}
		return new Checked(verdict.output(), null);
	}
}
