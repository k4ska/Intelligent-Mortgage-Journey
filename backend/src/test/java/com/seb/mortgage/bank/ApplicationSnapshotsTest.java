package com.seb.mortgage.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.seb.mortgage.documents.DocumentService;
import com.seb.mortgage.documents.DocumentType;
import com.seb.mortgage.profile.ProfileField;
import com.seb.mortgage.profile.ProfileKey;
import com.seb.mortgage.profile.ProfileService;
import com.seb.mortgage.property.PropertyKey;
import com.seb.mortgage.property.PropertyService;

/** What a sent application freezes (#86): profile, property and uploaded documents. */
@SpringBootTest
class ApplicationSnapshotsTest {

	@Autowired ApplicationSnapshots snapshots;
	@Autowired ProfileService profile;
	@Autowired PropertyService property;
	@Autowired DocumentService documents;
	@Autowired OfferCalculator calculator;
	@Autowired BankCatalogService catalog;

	@Test
	void freezesTheProfileThePropertyAndTheUploadedDocuments() {
		UUID user = UUID.randomUUID();
		profile.save(user, ProfileKey.NET_INCOME, "2350", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		property.updateFromChat(user, Map.of(PropertyKey.PRICE, "200000"), ProfileField.Status.CONFIRMED, false);
		documents.request(user, DocumentType.ID);
		documents.upload(user, DocumentType.ID, "id.pdf");
		documents.request(user, DocumentType.PAYSLIP);

		OfferCalculator.Snapshot snapshot = snapshots.read(snapshots.freeze(user));

		assertThat(snapshot.fields()).anyMatch(field -> field.key().equals("netIncome") && field.value().equals("2350"));
		assertThat(snapshot.property().fields())
				.anyMatch(field -> field.key().equals("price") && field.value().equals("200000"));
		assertThat(snapshot.documents()).containsExactly(new OfferCalculator.DocumentSnapshot("ID", "UPLOADED"));
	}

	@Test
	void anApplicationWithoutAPropertyHasNone() {
		UUID user = UUID.randomUUID();
		profile.save(user, ProfileKey.NET_INCOME, "2350", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);

		String json = snapshots.freeze(user);

		assertThat(json).contains("\"property\":null");
		assertThat(snapshots.read(json).property()).isNull();
		assertThat(snapshots.read(json).documents()).isEmpty();
	}

	@Test
	void anApplicationSentBeforeTheChangeStillReads() {
		OfferCalculator.Snapshot snapshot = snapshots.read(
				"[{\"key\":\"netIncome\",\"value\":\"2350\",\"source\":\"CHAT\",\"status\":\"CONFIRMED\"}]");

		assertThat(snapshot.fields()).singleElement().satisfies(field -> assertThat(field.value()).isEqualTo("2350"));
		assertThat(snapshot.property()).isNull();
		assertThat(snapshot.documents()).isEmpty();
	}

	@Test
	void aValuationThatWasOnlyAskedForIsLeftOut() {
		UUID user = UUID.randomUUID();
		profile.save(user, ProfileKey.NET_INCOME, "3000", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		profile.save(user, ProfileKey.SAVINGS, "40000", ProfileField.Source.CHAT, ProfileField.Status.CONFIRMED);
		property.updateFromChat(user, Map.of(PropertyKey.TYPE, "APARTMENT", PropertyKey.PRICE, "200000",
				PropertyKey.LOCATION, "Tallinn"), ProfileField.Status.CONFIRMED, false);
		documents.request(user, DocumentType.VALUATION);

		OfferCalculator.Snapshot snapshot = snapshots.read(snapshots.freeze(user));
		OfferCalculator.Decision decision = calculator.calculate(snapshot, catalog.find("seb"));

		assertThat(snapshot.documents()).isEmpty();
		assertThat(decision.type()).isEqualTo(OfferCalculator.DecisionType.OFFER);
		assertThat(decision.offer().conditions()).contains("Valuation confirms the property value");
	}
}
