package com.seb.mortgage.advisor;

import com.seb.mortgage.agent.LlmSpecialistAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
 * reply streams token by token. A turn where it uses tools acts in one or more rounds, then
 * replies (see {@link #reply}).
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
			  whenever they give a fact it was missing, right after saving that fact. If a tool says facts are missing, ask for the first one and wait; don't
			  guess any of them. The tools choose the loan term and interest rate themselves,
			  so never ask the user about those.
			- Explain an affordability result in plain words with only the tool's numbers: the
			  monthly payment, and what the stress test and the 50% limit mean for them. If the
			  home is too expensive, say why, then describe what they could afford instead: the
			  price range, the size it buys, or a cheaper area.
			- Never invent numbers. Only use numbers the user gave you or a tool returned. Never
			  save a value the user hasn't told you, not even 0, a typical value or something
			  that seems likely (like "employed").
			- When the user says in their message that they have none of something (no other
			  loans, no credit cards, no children), that is a value they told you: save it as 0
			  straight away, quoting those words.
			- Never promise that a bank will approve a loan, and never recommend one specific
			  bank over another.
			- Only when the user asks to apply or go to the banks, call showBankPicker. Never
			  call it on your own. If it says the user isn't ready, ask for what is missing.
			- When the user asks what a term means or how something works (Euribor, margin,
			  loan-to-value, stress test, valuation, ...), call explainTerm and base your answer
			  on its entry, in your own simple words. If it isn't in the glossary, keep it short
			  and general.
			- Answer in English. All amounts are in euros.""";

	/**
	 * Most act rounds in one turn, so the advisor can save a fact and then check something
	 * with it (#94) without looping forever.
	 */
	static final int MAX_ACT_ROUNDS = 3;

	public AdvisorAgent(ChatClient.Builder chatClientBuilder) {
		super(chatClientBuilder, SYSTEM);
	}

	/**
	 * One turn, in two steps when the advisor uses a tool (#88):
	 * <ol>
	 * <li><b>Act</b>: the model sees the tools. If it calls none, its answer is the reply.
	 * If it calls some, our code runs them and whatever text the model wrote next to the
	 * calls is thrown away. Then it acts again, seeing its calls and what they returned,
	 * so it can use one tool's result in the next (save the income, then check
	 * affordability, #94), up to {@link #MAX_ACT_ROUNDS} rounds.</li>
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
		return Mono.fromCallable(() -> act(earlier, userText, profileJson, tools))
				.flatMapMany(act -> {
					if (act.toolResults().isEmpty()) {
						log.info("Advisor turn: no tools, one call");
						return act.text() == null || act.text().isEmpty() ? Flux.<String>empty() : Flux.just(act.text());
					}
					log.info("Advisor turn: tools {} in {} act calls, then a reply call", act.toolNames(), act.calls());
					return replyAfterTools(earlier, userText, profileJson.get(), act.toolResults());
				});
	}

	/**
	 * What the act step came to: the model's text (only when it called no tools), or the
	 * tools it called and what they said, over {@code calls} model calls.
	 */
	record Act(String text, List<String> toolNames, List<String> toolResults, int calls) {}

	private Act act(List<Message> earlier, String userText, Supplier<String> profileJson, AdvisorTools tools) {
		Map<String, ToolCallback> callbacks = new HashMap<>();
		for (ToolCallback callback : ToolCallbacks.from(tools)) {
			callbacks.put(callback.getToolDefinition().name(), callback);
		}
		List<String> names = new ArrayList<>();
		List<String> results = new ArrayList<>();
		// The earlier rounds' calls and what they returned, for the next round to build on.
		List<Message> rounds = new ArrayList<>();
		for (int round = 1; round <= MAX_ACT_ROUNDS; round++) {
			List<Message> messages = withProfile(profileJson.get(), earlier);
			messages.add(new UserMessage(userText));
			messages.addAll(rounds);
			// Our code runs the tools below, so ChatClient's own tool loop is switched off.
			ChatResponse response = chatClient.prompt()
					.messages(messages)
					.tools(tools)
					.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
					.call()
					.chatResponse();
			AssistantMessage output = response == null || response.getResult() == null ? null
					: response.getResult().getOutput();
			if (output == null || !output.hasToolCalls()) {
				// Without tools the first answer is the reply; after tools, the reply call writes it.
				String text = round == 1 && output != null ? output.getText() : null;
				return new Act(text, names, results, round);
			}
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (AssistantMessage.ToolCall call : output.getToolCalls()) {
				String result = run(callbacks.get(call.name()), call);
				names.add(call.name());
				results.add(call.name() + ": " + result);
				tools.record(call.name(), result);
				responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
			}
			// The next round sees the calls and their results, never the text written next to them.
			rounds.add(AssistantMessage.builder().content("").toolCalls(output.getToolCalls()).build());
			rounds.add(ToolResponseMessage.builder().responses(responses).build());
		}
		return new Act(null, names, results, MAX_ACT_ROUNDS);
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
