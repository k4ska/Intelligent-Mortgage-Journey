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
}
