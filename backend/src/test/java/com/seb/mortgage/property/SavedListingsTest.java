package com.seb.mortgage.property;

import static com.seb.mortgage.property.ListingParserTest.APARTMENT_URL;
import static com.seb.mortgage.property.ListingParserTest.page;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The demo listing saved with the app is read without contacting kv.ee; other links aren't affected. */
class SavedListingsTest {

	static final String SAVED_URL = "https://www.kv.ee/hei-valmimas-on-rahulikus-peresobralikus-rajoonis-3533251";

	private final List<URI> fetched = new ArrayList<>();

	private final ListingReader reader = new ListingReader(ListingReader.savedFirst(new SavedListings(), uri -> {
		fetched.add(uri);
		return new ListingReader.Page(200, page("kv-apartment.html"));
	}));

	@Test
	void theSavedListingIsReadFromTheSavedCopy() {
		assertThat(reader.read(SAVED_URL)).isInstanceOfSatisfying(ListingReader.Lookup.Found.class, found -> {
			assertThat(found.values()).containsEntry(PropertyKey.PRICE, "478000");
			assertThat(found.values()).containsEntry(PropertyKey.LOCATION, "Kaare tn 3, Kopli, Rae vald");
		});
		assertThat(fetched).isEmpty();
	}

	@Test
	void theLinkCanBePastedInAnyUsualForm() {
		for (String link : List.of("kv.ee/hei-valmimas-on-rahulikus-peresobralikus-rajoonis-3533251?utm_source=fb",
				"https://www.kv.ee/en/some-other-slug-3533251", "http://kv.ee/3533251")) {
			assertThat(reader.read(link)).as(link).isInstanceOf(ListingReader.Lookup.Found.class);
		}
		assertThat(fetched).isEmpty();
	}

	@Test
	void otherListingsStillGoToKvEe() {
		assertThat(reader.read(APARTMENT_URL)).isInstanceOf(ListingReader.Lookup.Found.class);
		assertThat(fetched).hasSize(1);
	}
}
