package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DatabaseEmailGatewayTest {

	@Autowired EmailGateway gateway;
	@Autowired EmailThreadRepository threads;
	@Autowired EmailRepository emails;

	@Test
	void aNewThreadIsOpenAndUnreadAndKeepsItsBankEmails() {
		UUID applicationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		EmailThread thread = gateway.createThread(applicationId, userId, "seb");
		gateway.addBankEmail(thread.getId(), new BankAgent.Draft("question", "Subject", "Body"));

		EmailThread saved = threads.findByIdAndUserId(thread.getId(), userId).orElseThrow();
		assertThat(saved.getApplicationId()).isEqualTo(applicationId);
		assertThat(saved.getBankId()).isEqualTo("seb");
		assertThat(saved.getStatus()).isEqualTo(EmailThread.Status.OPEN);
		assertThat(saved.isUnread()).isTrue();

		assertThat(emails.findByThreadIdOrderByCreatedAtAsc(thread.getId()))
				.singleElement()
				.satisfies(email -> {
					assertThat(email.getFrom()).isEqualTo(Email.Sender.BANK);
					assertThat(email.getType()).isEqualTo("question");
					assertThat(email.getSubject()).isEqualTo("Subject");
					assertThat(email.getBody()).isEqualTo("Body");
				});
	}

	@Test
	void aThreadIsOnlyFoundForItsOwnUser() {
		EmailThread thread = gateway.createThread(UUID.randomUUID(), UUID.randomUUID(), "lhv");

		assertThat(threads.findByIdAndUserId(thread.getId(), UUID.randomUUID())).isEmpty();
	}
}
