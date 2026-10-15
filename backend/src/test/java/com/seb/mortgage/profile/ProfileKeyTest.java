package com.seb.mortgage.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

/** What the advisor sends is stored in one plain form, or rejected with a message it can act on. */
class ProfileKeyTest {

	@Test
	void amountsBecomePlainNumbers() {
		assertThat(ProfileKey.NET_INCOME.normalize("2350")).isEqualTo("2350");
		assertThat(ProfileKey.NET_INCOME.normalize("€2 350")).isEqualTo("2350");
		assertThat(ProfileKey.SAVINGS.normalize("30000.50")).isEqualTo("30000.5");
		assertThat(ProfileKey.SAVINGS.normalize("1.0E4")).isEqualTo("10000");
	}

	@Test
	void ambiguousOrNegativeAmountsAreRejected() {
		// "2,350" could be 2350 or 2.35 depending on the language, so it isn't guessed.
		assertThatThrownBy(() -> ProfileKey.NET_INCOME.normalize("2,350"))
				.hasMessageContaining("plain number");
		assertThatThrownBy(() -> ProfileKey.NET_INCOME.normalize("about two grand"))
				.hasMessageContaining("plain number");
		assertThatThrownBy(() -> ProfileKey.NET_INCOME.normalize("-5")).hasMessageContaining("negative");
		assertThatThrownBy(() -> ProfileKey.NET_INCOME.normalize(" ")).hasMessageContaining("needs a value");
	}

	@Test
	void countsAreWholeNumbers() {
		assertThat(ProfileKey.DEPENDANTS.normalize("2")).isEqualTo("2");
		assertThat(ProfileKey.DEPENDANTS.normalize("2.0")).isEqualTo("2");
		assertThatThrownBy(() -> ProfileKey.DEPENDANTS.normalize("1.5")).hasMessageContaining("whole number");
	}

	@Test
	void yesNoAnswers() {
		assertThat(ProfileKey.HAS_CO_APPLICANT.normalize("Yes")).isEqualTo("yes");
		assertThat(ProfileKey.HAS_CO_APPLICANT.normalize("false")).isEqualTo("no");
		assertThatThrownBy(() -> ProfileKey.HAS_CO_APPLICANT.normalize("maybe")).hasMessageContaining("yes or no");
	}

	@Test
	void textIsTrimmed() {
		assertThat(ProfileKey.EMPLOYMENT.normalize("  self-employed ")).isEqualTo("self-employed");
	}

	@Test
	void keysAreLookedUpByTheirApiName() {
		assertThat(ProfileKey.fromKey("netIncome")).contains(ProfileKey.NET_INCOME);
		assertThat(ProfileKey.fromKey("salary")).isEmpty();
	}
}
