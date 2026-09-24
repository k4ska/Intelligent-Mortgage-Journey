package com.seb.mortgage.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts LLM tokens per day and per agent, logs every call, and enforces a daily token
 * budget so a long testing session can't burn through the API key's daily limit.
 *
 * <p>The day rolls over at midnight in {@code app.token-budget.zone} (Europe/Tallinn by
 * default, matching when the key's limit resets). Totals live in memory only: a backend
 * restart resets them to zero, so the provider's own dashboard stays the source of truth.
 */
@Component
public class TokenUsageTracker {

	private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);
	private static final double WARN_AT = 0.8;

	private final long dailyBudget;
	private final Clock clock;

	private LocalDate day;
	private long used;
	private boolean warned;
	private boolean exceededLogged;
	private final Map<String, AgentTotals> byAgent = new LinkedHashMap<>();

	@Autowired
	public TokenUsageTracker(@Value("${app.token-budget.daily-limit:4500000}") long dailyBudget,
			@Value("${app.token-budget.zone:Europe/Tallinn}") String zone) {
		this(dailyBudget, Clock.system(ZoneId.of(zone)));
	}

	TokenUsageTracker(long dailyBudget, Clock clock) {
		this.dailyBudget = dailyBudget;
		this.clock = clock;
		this.day = LocalDate.now(clock);
	}

	public record AgentTotals(long calls, long inputTokens, long outputTokens, long totalTokens) {

		AgentTotals plus(long input, long output) {
			return new AgentTotals(calls + 1, inputTokens + input, outputTokens + output,
					totalTokens + input + output);
		}
	}

	/** What {@code GET /api/agent/usage} returns. */
	public record UsageSnapshot(LocalDate day, String resetsAt, long dailyBudget, long usedTokens,
			long remainingTokens, boolean budgetExceeded, Map<String, AgentTotals> byAgent) {}

	public synchronized void record(String agent, String model, long inputTokens, long outputTokens) {
		rollOverIfNewDay();
		long total = inputTokens + outputTokens;
		used += total;
		byAgent.merge(agent, new AgentTotals(1, inputTokens, outputTokens, total),
				(old, ignored) -> old.plus(inputTokens, outputTokens));

		log.info("[tokens] {} {} in={} out={} total={} | today: {} / {}",
				pad(agent, 14), pad(model, 12), fmt(inputTokens), fmt(outputTokens), fmt(total),
				fmt(used), fmt(dailyBudget));

		if (!warned && used >= dailyBudget * WARN_AT && used < dailyBudget) {
			warned = true;
			log.warn("[tokens] {}% of today's token budget used ({} / {})",
					Math.round(WARN_AT * 100), fmt(used), fmt(dailyBudget));
		}
	}

	/** True once today's budget is spent; the agents then fall back to the stub. */
	public synchronized boolean budgetExceeded() {
		rollOverIfNewDay();
		boolean exceeded = used >= dailyBudget;
		if (exceeded && !exceededLogged) {
			exceededLogged = true;
			log.warn("[tokens] Daily token budget reached ({} / {}). Falling back to the stub "
					+ "agent until midnight.", fmt(used), fmt(dailyBudget));
		}
		return exceeded;
	}

	public synchronized UsageSnapshot snapshot() {
		rollOverIfNewDay();
		ZonedDateTime reset = day.plusDays(1).atStartOfDay(clock.getZone());
		return new UsageSnapshot(day, reset.toOffsetDateTime().toString(), dailyBudget, used,
				Math.max(0, dailyBudget - used), used >= dailyBudget, new LinkedHashMap<>(byAgent));
	}

	private void rollOverIfNewDay() {
		LocalDate today = LocalDate.now(clock);
		if (!today.equals(day)) {
			day = today;
			used = 0;
			warned = false;
			exceededLogged = false;
			byAgent.clear();
		}
	}

	private static String fmt(long n) {
		return String.format("%,d", n);
	}

	private static String pad(String s, int width) {
		return String.format("%-" + width + "s", s == null ? "?" : s);
	}
}
