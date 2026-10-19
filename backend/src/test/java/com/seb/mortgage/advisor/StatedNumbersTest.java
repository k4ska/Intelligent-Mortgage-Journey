package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Which numbers count as said by the user. */
class StatedNumbersTest {

	private static StatedNumbers said(String text) {
		return StatedNumbers.from(List.of(), text);
	}

	@Test
	void readsNumbersTheWayPeopleTypeThem() {
		assertThat(said("I earn 3300 a month").contains("3300")).isTrue();
		assertThat(said("about 3 300 €").contains("3300")).isTrue();
		assertThat(said("about 3,300").contains("3300")).isTrue();
		assertThat(said("3.3k after tax").contains("3300")).isTrue();
		assertThat(said("3,5 tuhat").contains("3500")).isTrue();
		assertThat(said("€478,000").contains("478000")).isTrue();
		assertThat(said("we have two kids").contains("2")).isTrue();
		assertThat(said("meil on kaks last").contains("2")).isTrue();
	}

	@Test
	void numbersTheUserNeverWroteAreNotThere() {
		StatedNumbers stated = said("It's a house at Kaare tn 3 for 478000");

		assertThat(stated.contains("3300")).isFalse();
		assertThat(stated.contains("0")).isFalse();
		assertThat(stated.contains("478")).isFalse();
		assertThat(stated.contains("not a number")).isFalse();
	}

	@Test
	void earlierMessagesCount() {
		assertThat(StatedNumbers.from(List.of("I earn 2350"), "and my savings?").contains("2350")).isTrue();
	}

	@Test
	void noMeansZeroOnlyInTheMessageBeingAnswered() {
		assertThat(said("No, nothing").contains("0")).isTrue();
		assertThat(said("Pole laenu").contains("0")).isTrue();
		assertThat(said("I have 0 savings").contains("0")).isTrue();
		assertThat(StatedNumbers.from(List.of("No, I'm buying alone"), "My income is 2000").contains("0")).isFalse();
	}
}
