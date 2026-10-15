package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.LlmSpecialistAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
			- Use plain, warm language and short replies. Ask one simple question at a time.
			- When the user tells you a fact about themselves that updateProfile accepts (like
			  their income or savings), save it with updateProfile straight away.
			- Don't ask again for something that is already in the user's profile.
			- Never invent numbers. Only use numbers the user gave you or a tool returned.
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
	 * @param profileJson the user's profile fields as JSON, so the advisor knows what it knows
	 * @param tools       this turn's tools, acting for this user
	 */
	Flux<String> reply(List<Message> earlier, String userText, String profileJson, AdvisorTools tools) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("What the user's profile says so far (JSON): " + profileJson));
		messages.addAll(earlier);
		return chatClient.prompt()
				.messages(messages)
				.user(userText)
				.tools(tools)
				.stream()
				.content();
	}
}
