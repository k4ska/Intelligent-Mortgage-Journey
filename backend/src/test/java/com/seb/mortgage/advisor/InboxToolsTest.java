package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.seb.mortgage.bank.BankAgent;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.bank.EmailGateway;
import com.seb.mortgage.bank.EmailThread;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;

/** The advisor's draftReply tool (#80): it only drafts, next to a thread, with figures from somewhere. */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
class InboxToolsTest {

	static final String OFFER = "We can offer you 150000 euros over 25 years at a margin of 1.8%.";

	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired ListingReader listings;
	@Autowired BankCatalogService banks;
	@Autowired LoanProfileAssembler loans;
	@Autowired DocumentService documents;
	@Autowired Phases phases;
	@Autowired EmailGateway gateway;
	@Autowired BankThreads bankThreads;
	@Autowired ReplyDrafts drafts;
	@Autowired JdbcTemplate jdbc;

	private final List<StreamEvent> events = new ArrayList<>();
	private final UUID userId = UUID.randomUUID();

	private AdvisorTools tools(AdvisorTools.Inbox inbox) {
		return new AdvisorTools(userId, profile, property, listings, banks, loans, documents, phases,
				UserStatements.from(List.of(), ""), events::add, inbox);
	}

	private AdvisorTools nextTo(UUID threadId) {
		return tools(new AdvisorTools.Inbox(bankThreads.find(userId, threadId).orElseThrow(), drafts,
				List.of("Help me ask for a lower margin")));
	}

	private UUID thread() {
		EmailThread thread = gateway.createThread(UUID.randomUUID(), userId, "lhv");
		gateway.addBankEmail(thread.getId(), new BankAgent.Draft("offer", "Your mortgage offer", OFFER));
		return thread.getId();
	}

	@Test
	void aDraftGoesToTheReplyBoxOfTheThread() {
		UUID threadId = thread();

		String note = nextTo(threadId).draftReply("Thank you. Could you lower the margin from 1.8%?");

		assertThat(events).containsExactly(
				StreamEvent.draftReply(threadId, "Thank you. Could you lower the margin from 1.8%?"));
		assertThat(note).startsWith("The draft is now in the reply box for LHV").contains("don't say it was sent");
	}

	@Test
	void aDraftWithAFigureFromNowhereIsRefused() {
		UUID threadId = thread();

		String note = nextTo(threadId).draftReply("Could you lower the margin to 1.2%?");

		assertThat(events).isEmpty();
		assertThat(note).startsWith("Not drafted: these figures appear in none of").contains("1.2%");
	}

	@Test
	void inTheChatThereIsNoThreadToDraftFor() {
		AdvisorTools chat = tools(null);

		assertThat(chat.offers("draftReply")).isFalse();
		assertThat(chat.offers("updateProfile")).isTrue();
		assertThat(nextTo(thread()).offers("draftReply")).isTrue();
		assertThat(chat.draftReply("Hello")).startsWith("Drafts only work next to a bank's email thread");
		assertThat(events).isEmpty();
	}

	/** A closed thread still takes a last "no thanks" (docs/architecture.md §6); an accepted one doesn't. */
	@Test
	void onlyOpenOrClosedThreadsGetADraft() {
		UUID closed = thread();
		UUID accepted = thread();
		status(closed, EmailThread.Status.CLOSED);
		status(accepted, EmailThread.Status.ACCEPTED);

		nextTo(closed).draftReply("Thank you, but I have accepted another offer.");
		String note = nextTo(accepted).draftReply("Thank you!");

		assertThat(events).containsExactly(
				StreamEvent.draftReply(closed, "Thank you, but I have accepted another offer."));
		assertThat(note).isEqualTo("The thread with LHV is accepted, so the user can't reply there any more; "
				+ "nothing was drafted.");
	}

	private void status(UUID threadId, EmailThread.Status status) {
		jdbc.update("UPDATE email_thread SET status = ? WHERE id = ?", status.name(), threadId);
	}
}
