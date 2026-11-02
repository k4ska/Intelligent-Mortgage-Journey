package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.bank.BankAgent;
import com.seb.mortgage.bank.EmailGateway;
import com.seb.mortgage.bank.EmailThread;

/** The bank threads as the advisor sees them: a counter-offer replaces the earlier offer (#118). */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class BankThreadsTest {

	static final String FIRST = "We can offer €163 000 at a margin of 1.65%.";
	static final String COUNTER = "We can lower our margin to 1.35%. This is our final offer.";

	@Autowired EmailGateway gateway;
	@Autowired BankThreads bankThreads;

	private final UUID userId = UUID.randomUUID();

	@Test
	void theNewestOfferIsTheCurrentOneAndTheOlderOneIsMarkedReplaced() {
		EmailThread thread = gateway.createThread(UUID.randomUUID(), userId, "seb");
		gateway.addBankEmail(thread.getId(), new BankAgent.Draft("offer", "Your mortgage offer", FIRST));
		gateway.addBankEmail(thread.getId(), new BankAgent.Draft("offer", "Our updated offer", COUNTER));

		BankThreads.BankThread view = bankThreads.find(userId, thread.getId()).orElseThrow();

		assertThat(view.currentOffer()).isEqualTo(COUNTER);
		assertThat(view.emails()).extracting(BankThreads.BankEmail::type)
				.containsExactly(BankThreads.REPLACED_OFFER, "offer");
	}

	@Test
	void aThreadWithoutAnOfferHasNoCurrentOffer() {
		EmailThread thread = gateway.createThread(UUID.randomUUID(), userId, "lhv");
		gateway.addBankEmail(thread.getId(), new BankAgent.Draft("question", "Your application", "What is your income?"));

		assertThat(bankThreads.find(userId, thread.getId()).orElseThrow().currentOffer()).isNull();
	}
}
