package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class InboxPreviewTest {

	@Test
	void aShortBodyIsShownWholeOnOneLine() {
		assertThat(InboxController.preview("Hello,\n\nthanks.")).isEqualTo("Hello, thanks.");
	}

	@Test
	void aLongBodyIsCutWithAnEllipsis() {
		String preview = InboxController.preview("word ".repeat(100));

		assertThat(preview).hasSizeLessThanOrEqualTo(InboxController.PREVIEW_LENGTH).endsWith("…");
	}

	@Test
	void anOpenOfferLeadsThePreview() {
		OfferCalculator.Offer offer = new OfferCalculator.Offer(160000, 25, 2.1, 1.35, 3.45, 798.4, List.of(),
				LocalDate.of(2026, 12, 2));

		assertThat(InboxController.preview(offer, "confirmation", "We will be in touch soon."))
				.isEqualTo("Offer: 3.45%, €798.40 a month · We will be in touch soon.");
		assertThat(InboxController.preview(offer, "offer", "Thank you. We can offer €160 000 ..."))
				.isEqualTo("Offer: 3.45%, €798.40 a month, valid until 2026-12-02");
		assertThat(InboxController.preview(null, "confirmation", "We will be in touch soon."))
				.isEqualTo("We will be in touch soon.");
	}
}
