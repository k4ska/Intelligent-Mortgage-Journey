package com.seb.mortgage.documents;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.seb.mortgage.profile.ProfileKey;

/**
 * The documents a bank will need, with where to get each one in real life (the table in
 * {@code docs/vision.md}), why the bank needs it, tips for it, and which profile facts the
 * document reader takes from it.
 * Co-applicant documents only apply when the user buys together with someone.
 */
public enum DocumentType {

	ID("ID card or passport", "Your own ID card or passport.",
			"The bank has to know who it lends to.",
			"Check that it is valid for the next few months; an expired document can't be used.", false),
	PAYSLIP("Proof of income (payslips or an income statement)",
			"Your employer, or the Tax and Customs Board (EMTA) e-services.",
			"It shows that your income is real and regular, which decides how much you can borrow.",
			"Banks look at about the last 6 months. A permanent contract past its trial period counts best; if "
					+ "you changed jobs recently, tell the bank up front.",
			false, ProfileKey.NET_INCOME),
	BANK_STATEMENT("Bank statement, last 6 months", "Your internet bank.",
			"It shows your salary coming in, your regular spending and any loan payments the bank should count.",
			"In the months before applying, avoid new consumer loans or hire purchase, stay out of overdraft, "
					+ "close credit cards you don't use (banks count the limit), and have an explanation ready "
					+ "for any large or unusual transfers.",
			false, ProfileKey.MONTHLY_DEBT_PAYMENTS, ProfileKey.MONTHLY_LIVING_COSTS),
	DOWN_PAYMENT_PROOF("Proof of down payment funds", "Your internet bank (savings account statement).",
			"Banks lend only part of the price; it shows you have the rest yourself.",
			"Keep the money in your own account for a while before applying. If part of it is a gift from "
					+ "family, say so and be ready to show where it came from.",
			false, ProfileKey.SAVINGS),
	VALUATION("Valuation report",
			"A bank-approved licensed valuator, ordered after you have picked the property.",
			"The bank lends against what the home is worth, not just its asking price.",
			"Order it only once you have agreed on the home, from a valuator your bank accepts.", false),
	PURCHASE_AGREEMENT("Purchase agreement or reservation", "The seller or the real estate agent.",
			"It shows which home you are buying and on what terms.",
			"A reservation or preliminary agreement is usually enough to apply; check that its price matches "
					+ "what you told the bank.",
			false),
	CO_APPLICANT_ID("Co-applicant's ID card or passport", "The co-applicant.",
			"The bank has to know everyone who borrows.",
			"Check that it is valid for the next few months.", true),
	CO_APPLICANT_PAYSLIP("Co-applicant's proof of income", "The co-applicant: their employer or EMTA e-services.",
			"The bank counts both incomes, so it needs proof of theirs too.",
			"The same as yours: banks look at about the last 6 months of regular income.",
			true, ProfileKey.CO_APPLICANT_NET_INCOME);

	private final String label;
	private final String whereToGet;
	private final String why;
	private final String tips;
	private final boolean coApplicantOnly;
	private final List<ProfileKey> reads;

	DocumentType(String label, String whereToGet, String why, String tips, boolean coApplicantOnly,
			ProfileKey... reads) {
		this.label = label;
		this.whereToGet = whereToGet;
		this.why = why;
		this.tips = tips;
		this.coApplicantOnly = coApplicantOnly;
		this.reads = List.of(reads);
	}

	public String label() {
		return label;
	}

	public String whereToGet() {
		return whereToGet;
	}

	/** Why the bank needs it, in one plain sentence. */
	public String why() {
		return why;
	}

	/**
	 * How to make it count in the months before applying: only real preparation and being
	 * open with the bank, never changing or leaving anything out of the document.
	 */
	public String tips() {
		return tips;
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
