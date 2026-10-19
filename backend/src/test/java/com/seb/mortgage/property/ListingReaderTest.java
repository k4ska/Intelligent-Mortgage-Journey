package com.seb.mortgage.property;

import static com.seb.mortgage.property.ListingParserTest.APARTMENT_URL;
import static com.seb.mortgage.property.ListingParserTest.page;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/** Which links get fetched and what each answer becomes, with a fake fetcher: no network. */
class ListingReaderTest {

	private final List<URI> fetched = new ArrayList<>();

	private ListingReader answering(int status, String body) {
		return new ListingReader(uri -> {
			fetched.add(uri);
			return new ListingReader.Page(status, body);
		});
	}

	@Test
	void aReadableListingIsFound() {
		ListingReader.Lookup lookup = answering(200, page("kv-apartment.html")).read(APARTMENT_URL);

		assertThat(lookup).isInstanceOfSatisfying(ListingReader.Lookup.Found.class, found -> {
			assertThat(found.url()).isEqualTo(APARTMENT_URL);
			assertThat(found.values()).containsEntry(PropertyKey.PRICE, "189000");
		});
	}

	@Test
	void theBotCheckIsReportedAndNotWorkedAround() {
		ListingReader reader = answering(403, page("kv-bot-check.html"));

		assertThat(reader.read(APARTMENT_URL)).isInstanceOfSatisfying(ListingReader.Lookup.NotRead.class,
				notRead -> assertThat(notRead.reason()).contains("bot check"));
		assertThat(fetched).hasSize(1);
	}

	@Test
	void theBotCheckIsRecognisedEvenWithStatus200() {
		assertThat(answering(200, page("kv-bot-check.html")).read(APARTMENT_URL))
				.isInstanceOf(ListingReader.Lookup.NotRead.class);
	}

	@Test
	void aTimeoutOrNetworkErrorIsReported() {
		ListingReader slow = new ListingReader(uri -> { throw new TimeoutException(); });
		ListingReader down = new ListingReader(uri -> { throw new IOException("connection refused"); });

		assertThat(slow.read(APARTMENT_URL)).isEqualTo(new ListingReader.Lookup.NotRead("kv.ee didn't answer within 5 seconds"));
		assertThat(down.read(APARTMENT_URL)).isEqualTo(new ListingReader.Lookup.NotRead("kv.ee couldn't be reached"));
	}

	@Test
	void otherSitesAndNonListingPagesAreNeverFetched() {
		ListingReader reader = answering(200, page("kv-apartment.html"));

		for (String link : List.of("https://www.city24.ee/real-estate/3612345", "https://kv.ee.evil.com/3612345.html",
				"https://www.kv.ee/", "https://www.kv.ee/korterid", "ftp://www.kv.ee/3612345.html",
				"https://www.kv.ee:8443/3612345.html", "not a link", "")) {
			assertThat(reader.read(link)).as(link).isInstanceOf(ListingReader.Lookup.NotRead.class);
		}
		assertThat(fetched).isEmpty();
	}

	@Test
	void pagesRobotsTxtDisallowsAreNeverFetched() {
		ListingReader reader = answering(200, page("kv-apartment.html"));

		for (String link : List.of("https://www.kv.ee/object/images/3612345", "https://www.kv.ee/en/object/history/3612345",
				"https://www.kv.ee/printobj/3612345.html", "https://www.kv.ee/kuulutuse-ajalugu/3612345",
				"https://www.kv.ee/rent/2-toaline-korter-3612345.html", "https://www.kv.ee/uusarendus/images/3612345")) {
			assertThat(reader.read(link)).as(link).isInstanceOf(ListingReader.Lookup.NotRead.class);
		}
		assertThat(fetched).isEmpty();
	}

	@Test
	void eachLinkIsFetchedOnceEvenWithTrackingParameters() {
		ListingReader reader = answering(200, page("kv-apartment.html"));

		reader.read(APARTMENT_URL);
		reader.read(APARTMENT_URL + "?utm_source=facebook#gallery");
		reader.read("www.kv.ee/myya-3-toaline-korter-tallinn-parnu-mnt-41-3612345.html");

		assertThat(fetched).containsExactly(URI.create(APARTMENT_URL));
	}

	@Test
	void linksAreTidiedToHttps() {
		assertThat(ListingReader.listingUri("http://kv.ee/en/3612345")).isEqualTo(URI.create("https://kv.ee/en/3612345"));
		assertThat(ListingReader.listingUri(" https://WWW.KV.EE/3612345.html ")).isEqualTo(URI.create("https://www.kv.ee/3612345.html"));
	}
}
