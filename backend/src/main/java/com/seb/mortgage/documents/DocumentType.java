package com.seb.mortgage.documents;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.seb.mortgage.profile.ProfileKey;

/**
 * The documents a bank will need, with where to get each one in real life (the table in
 * {@code docs/vision.md}) and which profile facts the document reader takes from it.
 * Co-applicant documents only apply when the user buys together with someone.
 */
public enum DocumentType {

	ID("ID card or passport", "Your own ID card or passport.", false),
	PAYSLIP("Proof of income (payslips or an income statement)",
			"Your employer, or the Tax and Customs Board (EMTA) e-services.", false, ProfileKey.NET_INCOME),
	BANK_STATEMENT("Bank statement, last 6 months", "Your internet bank.", false,
			ProfileKey.MONTHLY_DEBT_PAYMENTS, ProfileKey.MONTHLY_LIVING_COSTS),
	DOWN_PAYMENT_PROOF("Proof of down payment funds", "Your internet bank (savings account statement).", false,
			ProfileKey.SAVINGS),
	VALUATION("Valuation report",
			"A bank-approved licensed valuator, ordered after you have picked the property.", false),
	PURCHASE_AGREEMENT("Purchase agreement or reservation", "The seller or the real estate agent.", false),
	CO_APPLICANT_ID("Co-applicant's ID card or passport", "The co-applicant.", true),
	CO_APPLICANT_PAYSLIP("Co-applicant's proof of income", "The co-applicant: their employer or EMTA e-services.",
			true, ProfileKey.CO_APPLICANT_NET_INCOME);

	private final String label;
	private final String whereToGet;
	private final boolean coApplicantOnly;
	private final List<ProfileKey> reads;

	DocumentType(String label, String whereToGet, boolean coApplicantOnly, ProfileKey... reads) {
		this.label = label;
		this.whereToGet = whereToGet;
		this.coApplicantOnly = coApplicantOnly;
		this.reads = List.of(reads);
	}

	public String label() {
		return label;
	}

	public String whereToGet() {
		return whereToGet;
	}

	public boolean coApplicantOnly() {
		return coApplicantOnly;
	}

	/** The profile facts the document reader takes from this document; none for an ID, for example. */
	public List<ProfileKey> reads() {
		return reads;
	}

	/** Accepts "PAYSLIP", "payslip" or "co-applicant-payslip". */
	public static Optional<DocumentType> parse(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		return Arrays.stream(values()).filter(type -> type.name().equals(normalized)).findFirst();
	}
}
