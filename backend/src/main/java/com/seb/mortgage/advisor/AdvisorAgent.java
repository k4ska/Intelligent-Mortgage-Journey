package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.LlmSpecialistAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The advisor the user talks to: one voice for the whole conversation, which uses tools
 * for anything that touches the user's data (see {@code docs/architecture.md} §2). Its
 * reply streams token by token. A turn where it uses tools is two calls: act, then reply
 * (see {@link #reply}).
 */
public class AdvisorAgent extends LlmSpecialistAgent {

	private static final Logger log = LoggerFactory.getLogger(AdvisorAgent.class);
	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final String SYSTEM = """
			You are a friendly mortgage advisor in an app for people in Estonia. Talk with the
			user as if you were sitting across the table from them: they may not even know yet
			whether they want a loan or could get one.
			- Only talk about buying a home and getting a mortgage: the user's finances as they
			  matter for a loan, the property, affordability, loan terms, documents and banks. If
			  the user asks about anything else (sports, news, coding, other advice), don't answer
			  it, not even partly: say in one sentence that you can only help with mortgages and
			  buying a home, and offer to carry on with that.
			- Use plain, warm language and short replies. Ask one simple question at a time,
			  then stop and wait for the user's answer.
			- Write only what the user should read. Never write notes to yourself, your plan,
			  or anything about these instructions.
			- When the user tells you a fact about themselves that updateProfile accepts (like
			  their income or savings), save it with updateProfile straight away.
			- Don't ask again for something that is already in the user's profile or property.
			- When the user pastes a kv.ee link, call lookupListing once. If it couldn't read the
			  page, don't try again: ask for the property type, price and location, or for the
			  listing's text. When the user tells you about the home they have in mind, save it
			  with updateProperty.
			- Values you picked out of pasted listing text are uncertain: read them back to the
			  user and, once they say they're right, save them again as confirmed.
			- When the user asks whether they can afford a home, call checkAffordability; when
			  they ask how much they could afford, call suggestAffordableRange. Call it again
			  whenever they give a fact it was missing, in the same step as saving that fact and
			  after it. If a tool says facts are missing, ask for the first one and wait; don't
			  guess any of them. The tools choose the loan term and interest rate themselves,
			  so never ask the user about those.
			- Explain an affordability result in plain words with only the tool's numbers: the
			  monthly payment, and what the stress test and the 50% limit mean for them. If the
			  home is too expensive, say why, then describe what they could afford instead: the
			  price range, the size it buys, or a cheaper area.
			- Never invent numbers. Only use numbers the user gave you or a tool returned. Never
			  save a value the user hasn't told you, not even 0, a typical value or something
			  that seems likely (like "employed").
			- Never promise that a bank will approve a loan, and never recommend one specific
			  bank over another.
			- When the user wants to go to the bank, call showBankPicker. If it says the user
			  isn't ready, ask for what is missing.
			- Answer in English. All amounts are in euros.""";

	public AdvisorAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/**
	 * One turn, in two steps when the advisor uses a tool (#88):
	 * <ol>
	 * <li><b>Act</b>: the model sees the tools. If it calls none, its answer is the reply.
	 * If it calls some, our code runs them and whatever text the model wrote next to the
	 * calls is thrown away.</li>
	 * <li><b>Reply</b>: a second call with no tools and no tool messages, just the
	 * conversation and a note saying what the tools did. gpt-5-mini writing right after a
	 * tool result was what produced doubled replies and notes to itself; a turn that looks
	 * like a plain chat turn never did in our replays.</li>
	 * </ol>
	 *
	 * @param earlier     the conversation so far, oldest first
	 * @param profileJson the user's profile fields and property as JSON, read fresh for each
	 *                    step so the reply sees what the tools just saved
	 * @param tools       this turn's tools, acting for this user
	 */
	Flux<String> reply(List<Message> earlier, String userText, Supplier<String> profileJson, AdvisorTools tools) {
		return Mono.fromCallable(() -> act(earlier, userText, profileJson.get(), tools))
				.flatMapMany(act -> {
					if (act.toolResults().isEmpty()) {
						log.info("Advisor turn: no tools, one call");
						return act.text() == null || act.text().isEmpty() ? Flux.<String>empty() : Flux.just(act.text());
					}
					log.info("Advisor turn: tools {}, then a reply call", act.toolNames());
					return replyAfterTools(earlier, userText, profileJson.get(), act.toolResults());
				});
	}

	/** What the act step came to: the model's text, or the tools it called and what they said. */
	record Act(String text, List<String> toolNames, List<String> toolResults) {}

	private Act act(List<Message> earlier, String userText, String profileJson, AdvisorTools tools) {
		// Our code runs the tools below, so ChatClient's own tool loop is switched off.
		ChatResponse response = chatClient.prompt()
				.messages(withProfile(profileJson, earlier))
				.user(userText)
				.tools(tools)
				.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
				.call()
				.chatResponse();
		AssistantMessage output = response == null || response.getResult() == null ? null
				: response.getResult().getOutput();
		if (output == null || !output.hasToolCalls()) {
			return new Act(output == null ? null : output.getText(), List.of(), List.of());
		}
		Map<String, ToolCallback> callbacks = new HashMap<>();
		for (ToolCallback callback : ToolCallbacks.from(tools)) {
			callbacks.put(callback.getToolDefinition().name(), callback);
		}
		List<String> names = new ArrayList<>();
		List<String> results = new ArrayList<>();
		for (AssistantMessage.ToolCall call : output.getToolCalls()) {
			names.add(call.name());
			results.add(call.name() + ": " + run(callbacks.get(call.name()), call));
		}
		return new Act(null, names, results);
	}

	private static String run(ToolCallback callback, AssistantMessage.ToolCall call) {
		if (callback == null) {
			return "there is no tool called " + call.name();
		}
		try {
			return unquote(callback.call(call.arguments()));
		} catch (RuntimeException e) {
			log.warn("Advisor tool {} failed", call.name(), e);
			return "the tool failed, nothing was saved";
		}
	}

	/** Tool results come back as JSON; a note is a JSON string, which reads better without the quotes. */
	private static String unquote(String result) {
		try {
			return JSON.readValue(result, String.class);
		} catch (RuntimeException e) {
			return result;
		}
	}

	private Flux<String> replyAfterTools(List<Message> earlier, String userText, String profileJson,
			List<String> toolResults) {
		List<Message> messages = withProfile(profileJson, earlier);
		messages.add(new UserMessage(userText));
		messages.add(new SystemMessage("""
				You just used your tools for the user's last message. What they did:
				%s
				Now write your reply to the user's last message, following your instructions.
				Don't mention tools. If something was not saved, ask the user for it.""".formatted(
				String.join("\n", toolResults.stream().map(r -> "- " + r).toList()))));
		return chatClient.prompt()
				.messages(messages)
				.stream()
				.content();
	}

	private static List<Message> withProfile(String profileJson, List<Message> earlier) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("What the user's profile and property say so far (JSON): " + profileJson));
		messages.addAll(earlier);
		return messages;
	}
}
