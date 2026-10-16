package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

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
}
