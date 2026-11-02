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
		assertThat(UserStatements.from(List.of(), "about 2400").hedged("2400")).isTrue();
		assertThat(UserStatements.from(List.of(), "idk maybe 1200").hedged("1200")).isTrue();
		assertThat(UserStatements.from(List.of(), "I spend ~900 a month").hedged("900 a month")).isTrue();
		assertThat(UserStatements.from(List.of(), "2000 or so").hedged("2000")).isTrue();
		assertThat(UserStatements.from(List.of(), "umbes 900").hedged("900")).isTrue();
		assertThat(UserStatements.from(List.of(), "I earn about 2400").hedged("I earn about 2400")).isTrue();
	}

	@Test
	void aHedgeFurtherAwayOrOnAnotherValueDoesnt() {
		UserStatements stated = UserStatements.from(List.of(),
				"I earn 2400 net a month and I have about 20000 saved");
		assertThat(stated.hedged("2400")).isFalse();
		assertThat(stated.hedged("20000")).isTrue();
		assertThat(UserStatements.from(List.of(), "I earn 2400").hedged("2400")).isFalse();
		assertThat(UserStatements.from(List.of(), "hi, we're a couple thinking about buying our first apartment")
				.hedged("we're a couple")).isFalse();
		assertThat(UserStatements.from(List.of("about 2400"), "yes, 2400 is right").hedged("2400 is right")).isFalse();
	}
}
