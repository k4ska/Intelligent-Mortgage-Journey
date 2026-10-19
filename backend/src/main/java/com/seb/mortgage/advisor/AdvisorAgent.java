package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.LlmSpecialistAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * The advisor the user talks to: one voice for the whole conversation, which uses tools
 * for anything that touches the user's data (see {@code docs/architecture.md} §2). Its
 * reply streams token by token; tool calls happen in the middle of the stream.
 */
public class AdvisorAgent extends LlmSpecialistAgent {

	private static final String SYSTEM = """
			You are a friendly mortgage advisor in an app for people in Estonia. Talk with the
			user as if you were sitting across the table from them: they may not even know yet
			whether they want a loan or could get one.
			- Use plain, warm language and short replies. Ask one simple question at a time,
			  then stop and wait for the user's answer.
			- Call any tools you need first, then write one reply. Don't write text alongside a
			  tool call; only your last message reaches the user.
			- When the user tells you a fact about themselves that updateProfile accepts (like
			  their income or savings), save it with updateProfile straight away.
			- Don't ask again for something that is already in the user's profile or property.
			- When the user pastes a kv.ee link, call lookupListing once. If it couldn't read the
			  page, don't try again: ask for the property type, price and location, or for the
			  listing's text. When the user tells you about the home they have in mind, save it
			  with updateProperty.
			- Values you picked out of pasted listing text are uncertain: read them back to the
			  user and, once they say they're right, save them again as confirmed.
			- Never invent numbers. Only use numbers the user gave you or a tool returned. Never
			  save a value the user hasn't told you, not even 0 or a typical value.
			- Never promise that a bank will approve a loan, and never recommend one specific
			  bank over another.
			- When the user wants to go to the bank, call showBankPicker. If it says the user
			  isn't ready, ask for what is missing.
			- Answer in English. All amounts are in euros.""";

	public AdvisorAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/**
	 * @param earlier     the conversation so far, oldest first
	 * @param profileJson the user's profile fields and property as JSON, so the advisor knows what it knows
	 * @param tools       this turn's tools, acting for this user
	 */
	Flux<String> reply(List<Message> earlier, String userText, String profileJson, AdvisorTools tools) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("What the user's profile and property say so far (JSON): " + profileJson));
		messages.addAll(earlier);
		return lastRoundText(chatClient.prompt()
				.messages(messages)
				.user(userText)
				.tools(tools)
				.stream()
				.chatResponse());
	}

	/**
	 * Only the text of the turn's last round. With tools, a turn is several model calls in a
	 * row; the text the model writes next to a tool call would otherwise reach the user as
	 * extra replies glued together (#88). Each call has its own response id, so text under a
	 * new id means the earlier round ended in a tool call: that text is dropped. The last
	 * round's text is sent once the turn is over, since only then is it known to be the last.
	 */
	static Flux<String> lastRoundText(Flux<ChatResponse> responses) {
		return Flux.defer(() -> {
			StringBuilder round = new StringBuilder();
			String[] roundId = { null };
			return responses
					.doOnNext(response -> {
						String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
						if (text == null || text.isEmpty()) {
							return; // usage-only or tool-call chunks say nothing about rounds
						}
						String id = response.getMetadata().getId();
						if (id != null && !id.isEmpty() && !id.equals(roundId[0])) {
							if (roundId[0] != null) {
								round.setLength(0);
							}
							roundId[0] = id;
						}
						round.append(text);
					})
					.thenMany(Flux.defer(() -> round.isEmpty() ? Flux.<String>empty() : Flux.just(round.toString())));
		});
	}
}
