package com.seb.mortgage.profile;

/**
 * A profile field as the API, the stream events and the bank snapshot show it.
 *
 * @param sourceDocument the document type it was read from (e.g. PAYSLIP), or null
 */
public record ProfileFieldView(String key, String value, ProfileField.Source source, ProfileField.Status status,
		String sourceDocument) {

	public ProfileFieldView(String key, String value, ProfileField.Source source, ProfileField.Status status) {
		this(key, value, source, status, null);
	}
}
