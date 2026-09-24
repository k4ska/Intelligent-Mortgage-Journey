package com.seb.mortgage.agent;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTrackerTest {

	private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

	/** A clock the test can move forward. */
	static class MutableClock extends Clock {
		Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		@Override public ZoneId getZone() { return TALLINN; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}

	@Test
	void sumsTokensPerAgentAndOverall() {
		TokenUsageTracker tracker = new TokenUsageTracker(1_000, Clock.system(TALLINN));

		tracker.record("ExplainAgent", "gpt-5-mini", 100, 20);
		tracker.record("ExplainAgent", "gpt-5-mini", 50, 10);
		tracker.record("VerifierAgent", "gpt-5-mini", 200, 5);

		TokenUsageTracker.UsageSnapshot s = tracker.snapshot();
		assertThat(s.usedTokens()).isEqualTo(385);
		assertThat(s.remainingTokens()).isEqualTo(615);
		assertThat(s.byAgent().get("ExplainAgent"))
				.isEqualTo(new TokenUsageTracker.AgentTotals(2, 150, 30, 180));
		assertThat(s.byAgent().get("VerifierAgent").totalTokens()).isEqualTo(205);
		assertThat(s.budgetExceeded()).isFalse();
	}

	@Test
	void budgetIsExceededOnceUsageReachesTheLimit() {
		TokenUsageTracker tracker = new TokenUsageTracker(100, Clock.system(TALLINN));
		tracker.record("ChatAgent", "gpt-5-mini", 60, 30);
		assertThat(tracker.budgetExceeded()).isFalse();

		tracker.record("ChatAgent", "gpt-5-mini", 5, 5);
		assertThat(tracker.budgetExceeded()).isTrue();
		assertThat(tracker.snapshot().remainingTokens()).isZero();
	}

	@Test
	void resetsAtMidnightTallinnTimeNotUtc() {
		// 23:30 in Tallinn (UTC+3 in September) is 20:30 UTC.
		MutableClock clock = new MutableClock(Instant.parse("2026-09-24T20:30:00Z"));
		TokenUsageTracker tracker = new TokenUsageTracker(100, clock);
		tracker.record("ChatAgent", "gpt-5-mini", 80, 20);
		assertThat(tracker.budgetExceeded()).isTrue();

		// 00:10 Tallinn on the 25th - still the 24th in UTC, but a new budget day here.
		clock.now = Instant.parse("2026-09-24T21:10:00Z");
		assertThat(clock.now.atOffset(ZoneOffset.UTC).getDayOfMonth()).isEqualTo(24);
		assertThat(tracker.budgetExceeded()).isFalse();
		assertThat(tracker.snapshot().usedTokens()).isZero();
		assertThat(tracker.snapshot().day()).hasToString("2026-09-25");
		assertThat(tracker.snapshot().resetsAt()).isEqualTo("2026-09-26T00:00+03:00");
	}
}
