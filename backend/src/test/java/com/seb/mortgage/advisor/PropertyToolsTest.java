package com.seb.mortgage.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.advisor.AdvisorTools.FieldUpdate;
import com.seb.mortgage.bank.BankCatalogService;
import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.profile.ProfileField.Source;
import com.seb.mortgage.profile.ProfileField.Status;
import com.seb.mortgage.profile.LoanProfileAssembler;
import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.ListingReader;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;

/** The property tools, called directly with a fake kv.ee: no LLM and no network. */
@SpringBootTest
class PropertyToolsTest {

	private static final String LINK = "https://www.kv.ee/myya-3-toaline-korter-tallinn-parnu-mnt-41-3612345.html";

	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired BankCatalogService banks;
	@Autowired LoanProfileAssembler loans;
	@Autowired DocumentService documents;

	private final UUID userId = UUID.randomUUID();
	private final List<StreamEvent> events = new ArrayList<>();
	/** What the user wrote this turn; the price is only saved if it appears in it. */
	private String userSaid = "It's 189000, they'd take 182000. Or the other one for 320 000";

	private AdvisorTools tools(ListingReader listings) {
		return new AdvisorTools(userId, profile, property, listings, banks, loans, documents,
				UserStatements.from(List.of(), userSaid), events::add);
	}

	/** A fact the user stated in {@link #userSaid}, quoting their whole message. */
	private FieldUpdate field(String key, String value) {
		return new FieldUpdate(key, value, userSaid);
	}

	private AdvisorTools tools() {
		return tools(kvEeAnswering(403, page("kv-bot-check.html")));
	}

	private static ListingReader kvEeAnswering(int status, String body) {
		return new ListingReader(uri -> new ListingReader.Page(status, body));
	}

	@Test
	void aReadableListingFillsInThePropertyFromTheListing() {
		String note = tools(kvEeAnswering(200, page("kv-apartment.html"))).lookupListing(LINK);

		PropertyView saved = property.active(userId).orElseThrow();
		assertThat(saved.listingUrl()).isEqualTo(LINK);
		assertThat(saved.fields()).extracting(ProfileFieldView::key)
				.containsExactly("type", "price", "location", "size", "rooms", "yearBuilt");
		assertThat(saved.fields()).allMatch(f -> f.source() == Source.LISTING && f.status() == Status.CONFIRMED);
		assertThat(events).containsExactly(StreamEvent.propertyUpdated(saved));
		assertThat(note).startsWith("Read the listing and saved: type=APARTMENT, price=189000")
				.doesNotContain("Still missing");
	}

	@Test
	void aBlockedLookupAsksTheUserAndChangesNothing() {
		String note = tools().lookupListing(LINK);

		assertThat(note).contains("bot check").contains("Don't try again")
				.contains("type").contains("price").contains("location").contains("paste the listing's text");
		assertThat(property.active(userId)).isEmpty();
		assertThat(events).isEmpty();
	}

	@Test
	void aLinkThatIsNotKvEeIsNotOpened() {
		assertThat(tools().lookupListing("https://www.city24.ee/real-estate/3612345"))
				.startsWith("Couldn't read the listing: that isn't a kv.ee listing link");
	}

	@Test
	void whatTheUserSaysIsConfirmed() {
		String note = tools().updateProperty(List.of(field("type", "apartment"),
				field("price", "189000")), null, null);

		assertThat(property.active(userId).orElseThrow().fields()).containsExactly(
				new ProfileFieldView("type", "APARTMENT", Source.CHAT, Status.CONFIRMED),
				new ProfileFieldView("price", "189000", Source.CHAT, Status.CONFIRMED));
		assertThat(note).isEqualTo("Saved: type=APARTMENT, price=189000. Still missing: location. Ask the user about it.");
		assertThat(events).hasSize(1);
	}

	@Test
	void pastedTextIsUncertainUntilTheUserConfirmsIt() {
		String note = tools().updateProperty(List.of(field("price", "189000"),
				field("location", "Pärnu mnt 41, Tallinn")), true, false);

		assertThat(note).startsWith("Saved as uncertain:").contains("ask if they're right");
		assertThat(property.active(userId).orElseThrow().fields()).allMatch(f -> f.status() == Status.UNCERTAIN);

		tools().updateProperty(List.of(field("price", "189000"),
				field("location", "Pärnu mnt 41, Tallinn")), false, false);

		assertThat(property.active(userId).orElseThrow().fields())
				.allMatch(f -> f.status() == Status.CONFIRMED && f.source() == Source.CHAT);
	}

	@Test
	void aCorrectionKeepsTheRestOfTheListing() {
		tools(kvEeAnswering(200, page("kv-apartment.html"))).lookupListing(LINK);

		tools().updateProperty(List.of(field("price", "182000")), false, false);

		PropertyView saved = property.active(userId).orElseThrow();
		assertThat(saved.listingUrl()).isEqualTo(LINK);
		assertThat(saved.fields()).contains(new ProfileFieldView("price", "182000", Source.CHAT, Status.CONFIRMED),
				new ProfileFieldView("rooms", "3", Source.LISTING, Status.CONFIRMED));
	}

	@Test
	void aNewPropertyReplacesTheOldOne() {
		tools(kvEeAnswering(200, page("kv-apartment.html"))).lookupListing(LINK);

		tools().updateProperty(List.of(field("type", "house"), field("price", "320000"),
				field("location", "Saue")), false, true);

		PropertyView saved = property.active(userId).orElseThrow();
		assertThat(saved.listingUrl()).isNull();
		assertThat(saved.fields()).extracting(ProfileFieldView::key).containsExactly("type", "price", "location");
	}

	@Test
	void listingOnlyValuesUnknownKeysAndBadValuesAreRejected() {
		String note = tools().updateProperty(List.of(field("rooms", "3"), field("garden", "yes"),
				field("type", "castle"), field("price", "lots")), null, null);

		assertThat(note).startsWith("Not saved:").contains("rooms is only read from a listing")
				.contains("garden is not an allowed key").contains("type must be one of").contains("price must be");
		assertThat(property.active(userId)).isEmpty();
		assertThat(events).isEmpty();
	}

	@Test
	void aPriceTheUserNeverWroteIsNotSaved() {
		userSaid = "It's an apartment in Tartu";

		String note = tools().updateProperty(List.of(field("type", "apartment"),
				field("price", "150000")), null, null);

		assertThat(note).contains("price=150000 was not saved because the user never said it");
		assertThat(property.active(userId).orElseThrow().fields()).extracting(ProfileFieldView::key).containsExactly("type");
	}

	@Test
	void eachToolActsOnlyForItsOwnUser() {
		tools().updateProperty(List.of(field("price", "189000")), null, null);

		assertThat(property.active(UUID.randomUUID())).isEmpty();
	}

	private static String page(String name) {
		try (InputStream in = PropertyToolsTest.class.getResourceAsStream("/listings/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
