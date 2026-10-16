package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The wall (docs/architecture.md §1): the bank side never sees the user's conversation with
 * the advisor, so nothing in the {@code bank} package may use the advisor or conversation code.
 */
class BankWallTest {

	private static final Path BANK_SOURCES = Path.of("src/main/java/com/seb/mortgage/bank");
	private static final Pattern FORBIDDEN = Pattern.compile("com\\.seb\\.mortgage\\.(advisor|conversation)\\b");

	@Test
	void theBankPackageUsesNothingFromTheAdvisorOrTheConversation() throws IOException {
		assertThat(BANK_SOURCES).isDirectory();
		try (Stream<Path> files = Files.walk(BANK_SOURCES)) {
			List<String> offenders = files.filter(file -> file.toString().endsWith(".java"))
					.filter(file -> FORBIDDEN.matcher(read(file)).find())
					.map(file -> file.getFileName().toString())
					.toList();
			assertThat(offenders).isEmpty();
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
