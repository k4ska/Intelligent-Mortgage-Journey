package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Which numbers count as said by the user. */
class UserStatementsTest {

	private static UserStatements said(String text) {
		return UserStatements.from(List.of(), text);
	}

	@Test
	void readsNumbersTheWayPeopleTypeThem() {
		assertThat(said("I earn 3300 a month").containsNumber("3300")).isTrue();
		assertThat(said("about 3 300 €").containsNumber("3300")).isTrue();
		assertThat(said("about 3,300").containsNumber("3300")).isTrue();
		assertThat(said("3.3k after tax").containsNumber("3300")).isTrue();
		assertThat(said("3,5 tuhat").containsNumber("3500")).isTrue();
		assertThat(said("€478,000").containsNumber("478000")).isTrue();
		assertThat(said("we have two kids").containsNumber("2")).isTrue();
		assertThat(said("meil on kaks last").containsNumber("2")).isTrue();
	}

	@Test
	void aCommaAndSpaceEndANumber() {
		UserStatements stated = said("its a 2 room apartment in kalamaja, 198 000, 52m2, built 1936 renovated");

		assertThat(stated.containsNumber("198000")).isTrue();
		assertThat(stated.containsNumber("52")).isTrue();
		assertThat(stated.containsNumber("1936")).isTrue();
		assertThat(stated.containsNumber("198000.52")).isFalse();
	}

	@Test
	void numbersTheUserNeverWroteAreNotThere() {
		UserStatements stated = said("It's a house at Kaare tn 3 for 478000");

		assertThat(stated.containsNumber("3300")).isFalse();
		assertThat(stated.containsNumber("0")).isFalse();
		assertThat(stated.containsNumber("478")).isFalse();
		assertThat(stated.containsNumber("not a number")).isFalse();
	}

	@Test
	void aQuoteMustBeTheUsersOwnWholeWords() {
		UserStatements stated = said("I work at a bank, and it's a house for 690 000.");

		assertThat(stated.containsQuote("I work at a bank")).isTrue();
		assertThat(stated.containsQuote("i WORK at a bank!")).isTrue();
		assertThat(stated.containsQuote("I'm employed")).isFalse();
		assertThat(stated.containsQuote("ban")).isFalse();
		assertThat(stated.containsQuote("")).isFalse();
		assertThat(stated.containsQuote(null)).isFalse();
	}

	@Test
	void earlierMessagesCount() {
		assertThat(UserStatements.from(List.of("I earn 2350"), "and my savings?").containsNumber("2350")).isTrue();
	}

	@Test
	void noMeansZeroOnlyInTheMessageBeingAnswered() {
		assertThat(said("No, nothing").containsNumber("0")).isTrue();
		assertThat(said("Pole laenu").containsNumber("0")).isTrue();
		assertThat(said("I have 0 savings").containsNumber("0")).isTrue();
		assertThat(UserStatements.from(List.of("No, I'm buying alone"), "My income is 2000").containsNumber("0")).isFalse();
	}

	@Test
	void aHedgeNextToTheValueMakesItAGuess() {
		assertThat(UserStatements.from(List.of(), "about 2400").hedged("about 2400", "2400")).isTrue();
		assertThat(UserStatements.from(List.of(), "idk maybe 1200").hedged("1200", "1200")).isTrue();
		assertThat(UserStatements.from(List.of(), "I spend ~900 a month").hedged("900 a month", "900")).isTrue();
		assertThat(UserStatements.from(List.of(), "2000 or so").hedged("2000", "2000")).isTrue();
		assertThat(UserStatements.from(List.of(), "umbes 900").hedged("900", "900")).isTrue();
		assertThat(UserStatements.from(List.of(), "about 40k saved").hedged("40k saved", "40000")).isTrue();
		assertThat(UserStatements.from(List.of(), "I think I'm self-employed")
				.hedged("I think I'm self-employed", "self-employed")).isTrue();
	}

	/** The stress test's messages (#115), and a quote of the whole message. */
	@Test
	void aHedgeOnlyCoversItsOwnClause() {
		UserStatements stated = UserStatements.from(List.of(), "i earn about 2400 net a month, my partner earns 2100");
		String whole = "i earn about 2400 net a month, my partner earns 2100";
		assertThat(stated.hedged(whole, "2400")).isTrue();
		assertThat(stated.hedged(whole, "2100")).isFalse();

		UserStatements living = UserStatements.from(List.of(),
				"we have 40000 saved, no other loans, no kids, idk maybe 1200 a month on living");
		assertThat(living.hedged("no kids", "0")).isFalse();
		assertThat(living.hedged("idk maybe 1200 a month on living", "1200")).isTrue();
		assertThat(living.hedged("we have 40000 saved", "40000")).isFalse();

		assertThat(UserStatements.from(List.of(), "I earn 2400 net a month and I have about 20000 saved")
				.hedged("I earn 2400 net a month", "2400")).isFalse();
		assertThat(UserStatements.from(List.of(), "hi, we're a couple thinking about buying our first apartment")
				.hedged("we're a couple", "yes")).isFalse();
		assertThat(UserStatements.from(List.of("about 2400"), "yes, 2400 is right").hedged("2400 is right", "2400"))
				.isFalse();
	}
}
