package com.seb.mortgage.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Reading a listing page's structured data, from saved pages: no network. */
class ListingParserTest {

	static final String APARTMENT_URL = "https://www.kv.ee/myya-3-toaline-korter-tallinn-parnu-mnt-41-3612345.html";

	static String page(String name) {
		try (InputStream in = ListingParserTest.class.getResourceAsStream("/listings/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void readsEveryValueFromTheStructuredData() {
		assertThat(ListingParser.parse(page("kv-apartment.html"), APARTMENT_URL)).containsOnly(
				entry(PropertyKey.TYPE, "APARTMENT"),
				entry(PropertyKey.PRICE, "189000"),
				entry(PropertyKey.LOCATION, "Pärnu mnt 41, Tallinn"),
				entry(PropertyKey.SIZE, "61.4"),
				entry(PropertyKey.ROOMS, "3"),
				entry(PropertyKey.YEAR_BUILT, "1937"));
	}

	/** A real kv.ee page (trimmed), so we know the parser fits what kv.ee actually publishes. */
	@Test
	void readsARealKvEeListing() {
		String url = "https://www.kv.ee/hei-valmimas-on-rahulikus-peresobralikus-rajoonis-3533251";

		assertThat(ListingReader.listingUri(url)).isNotNull();
		assertThat(ListingParser.parse(page("kv-house-real.html"), url)).containsOnly(
				// Built in 2026 and listed in 2026 ("valmimas", being completed): a new build.
				entry(PropertyKey.TYPE, "NEW_BUILD"),
				entry(PropertyKey.PRICE, "478000"),
				entry(PropertyKey.LOCATION, "Kaare tn 3, Kopli, Rae vald"),
				entry(PropertyKey.SIZE, "161"),
				entry(PropertyKey.ROOMS, "5"),
				entry(PropertyKey.YEAR_BUILT, "2026"));
	}

	@Test
	void anOlderHouseStaysAHouse() {
		String html = ldJson("""
				{ "@type": "RealEstateListing", "datePosted": "2026-09-26",
				  "mainEntity": { "@type": "House", "yearBuilt": 2001 } }""");

		assertThat(ListingParser.parse(html, "https://www.kv.ee/3600005")).containsEntry(PropertyKey.TYPE, "HOUSE");
	}

	@Test
	void theBotCheckPageHasNothingToRead() {
		assertThat(ListingParser.parse(page("kv-bot-check.html"), APARTMENT_URL)).isEmpty();
	}

	@Test
	void takesTheTypeFromTheWordsWhenTheSchemaDoesNotSay() {
		String html = ldJson("""
				{ "@type": "Product", "name": "Müüa maja, Saue vald", "offers": { "price": 320000 } }""");

		assertThat(ListingParser.parse(html, "https://www.kv.ee/3600001.html"))
				.containsEntry(PropertyKey.TYPE, "HOUSE")
				.containsEntry(PropertyKey.PRICE, "320000");
	}

	@Test
	void aNewDevelopmentIsANewBuildEvenIfItIsAnApartment() {
		String html = ldJson("""
				{ "@type": "Apartment", "name": "Uusarendus: 2-toaline korter, Tartu" }""");

		assertThat(ListingParser.parse(html, "https://www.kv.ee/3600002.html"))
				.containsEntry(PropertyKey.TYPE, "NEW_BUILD");
	}

	@Test
	void leavesOutPricesInOtherCurrenciesAndValuesThatMakeNoSense() {
		String html = ldJson("""
				{ "@type": "House", "offers": { "price": "250000", "priceCurrency": "USD" },
				  "numberOfRooms": "many", "address": "Pärnu" }""");

		assertThat(ListingParser.parse(html, "https://www.kv.ee/3600003.html")).containsOnly(
				entry(PropertyKey.TYPE, "HOUSE"),
				entry(PropertyKey.LOCATION, "Pärnu"));
	}

	@Test
	void aBrokenBlockDoesNotSpoilTheOthers() {
		String html = ldJson("{ not json") + ldJson("""
				{ "@graph": [ { "@type": "Offer", "price": 99000, "priceCurrency": "EUR" } ] }""");

		assertThat(ListingParser.parse(html, "https://www.kv.ee/3600004.html"))
				.containsEntry(PropertyKey.PRICE, "99000");
	}

	@Test
	void aPageWithoutStructuredDataGivesNothing() {
		assertThat(ListingParser.parse("<html><body>Hind 189 000 €</body></html>", APARTMENT_URL)).isEmpty();
	}

	private static String ldJson(String json) {
		return "<script type=\"application/ld+json\">" + json + "</script>";
	}
}
