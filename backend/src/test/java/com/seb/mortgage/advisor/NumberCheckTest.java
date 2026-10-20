package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The number check behind the verifier on flagged turns (#67). */
class NumberCheckTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"€2,350 | 2350",
			"2 350 € | 2350",
			"2 350 EUR | 2350",
			"€210k | 210000",
			"210 thousand euros | 210000",
			"€1.5 million | 1500000",
			"€976.50 | 976.50",
			"1.168 € | 1168",
			"3.5% | 3.5",
			"3,5 % | 3.5",
			"6.10 percent | 6.10"
	})
	void readsAFigureHoweverItIsWritten(String written, BigDecimal value) {
		assertThat(NumberCheck.figures("That is " + written + " in total."))
				.singleElement()
				.satisfies(figure -> assertThat(figure.value()).isEqualByComparingTo(value));
	}

	@Test
	void plainNumbersAndAreasAreNotFigures() {
		assertThat(NumberCheck.figures("A 3-room flat of 54 m² over 25 years, built in 1998.")).isEmpty();
		assertThat(NumberCheck.figures("54m² for €150,000")).extracting(NumberCheck.Figure::written)
				.containsExactly("€150,000");
	}

	/** Seen live: "up to about €146–151k" means 146,000 to 151,000. */
	@Test
	void aRangeAppliesItsScaleToBothEnds() {
		assertThat(NumberCheck.figures("homes for €146–151k")).extracting(NumberCheck.Figure::value)
				.usingElementComparator(BigDecimal::compareTo)
				.containsExactly(BigDecimal.valueOf(146_000), BigDecimal.valueOf(151_000));
		assertThat(NumberCheck.figures("a rate of 3 - 4 %")).extracting(NumberCheck.Figure::value)
				.usingElementComparator(BigDecimal::compareTo)
				.containsExactly(BigDecimal.valueOf(3), BigDecimal.valueOf(4));
		assertThat(NumberCheck.check("Look at €146–151k or €146k–€151k.", List.of("25 years: 146345, 30 years: 151249"))
				.passed()).isTrue();
	}

	@Test
	void aFigureCountsOnceEvenIfWrittenWithBothSigns() {
		assertThat(NumberCheck.figures("€2,350 EUR")).hasSize(1);
	}

	@Test
	void roundingWithinOnePercentPasses() {
		List<String> sources = List.of("maxLoan=209734");

		assertThat(NumberCheck.check("You could borrow about €210,000.", sources).passed()).isTrue();
		assertThat(NumberCheck.check("You could borrow about €210k.", sources).passed()).isTrue();
		assertThat(NumberCheck.check("You could borrow about €215,000.", sources).unsupported())
				.containsExactly("€215,000");
	}

	@Test
	void aFigureFromNowhereFails() {
		NumberCheck.Result result = NumberCheck.check("Your payment is €1,234 at 6.1%.", List.of("rate=6.10"));

		assertThat(result.passed()).isFalse();
		assertThat(result.unsupported()).containsExactly("€1,234");
	}

	@Test
	void aReplyWithoutFiguresPasses() {
		assertThat(NumberCheck.check("How much do you earn per month?", List.of()).passed()).isTrue();
		assertThat(NumberCheck.check(null, List.of()).passed()).isTrue();
	}

	/** Each thing the orchestrator passes in counts as a source. */
	@Test
	void takesFiguresFromEachSource() {
		String toolResult = "Affordable. Monthly payment 1 168.42, stress payment 1 402.10.";
		String profile = "{\"fields\":[{\"key\":\"netIncome\",\"value\":\"2350\"}],\"property\":{\"price\":185000}}";
		String userMessage = "We have 40k saved";
		String earlierReply = "The typical margin is 1.75%.";

		assertThat(NumberCheck.check("A payment of about €1,168 a month.", List.of(toolResult)).passed()).isTrue();
		assertThat(NumberCheck.check("On €2,350 net for a €185,000 flat.", List.of(profile)).passed()).isTrue();
		assertThat(NumberCheck.check("With your €40,000 in savings.", List.of(userMessage)).passed()).isTrue();
		assertThat(NumberCheck.check("Plus the 1.75% margin.", List.of(earlierReply)).passed()).isTrue();
		assertThat(NumberCheck.check("A payment of about €1,168 a month.", List.of(profile, userMessage)).passed())
				.isFalse();
	}

	@Test
	void zeroOnlyMatchesZero() {
		assertThat(NumberCheck.check("Savings: €0.", List.of("savings=0")).passed()).isTrue();
		assertThat(NumberCheck.check("Savings: €5.", List.of("savings=0")).passed()).isFalse();
	}
}
