package com.seb.mortgage.advisor;

import com.seb.mortgage.profile.ProfileFieldView;
import com.seb.mortgage.property.PropertyView;

import java.util.List;
import java.util.Map;

/**
 * One event in the stream of an advisor turn: the SSE event name and its JSON data.
 * The shapes are a contract with the frontend (see {@code docs/architecture.md} §5).
 */
public record StreamEvent(String type, Map<String, Object> data) {

	public record BankOption(String id, String name) {}

	/** A piece of the advisor's reply. */
	public static StreamEvent text(String delta) {
		return new StreamEvent("text", Map.of("delta", delta));
	}

	public static StreamEvent profileUpdated(List<ProfileFieldView> fields) {
		return new StreamEvent("profile_updated", Map.of("fields", fields));
	}

	public static StreamEvent propertyUpdated(PropertyView property) {
		return new StreamEvent("property_updated", Map.of("property", property));
	}

	/** Shows the bank picker card under the chat. */
	public static StreamEvent bankPicker(List<BankOption> banks) {
		return new StreamEvent("bank_picker", Map.of("banks", banks));
	}

	/** The verifier replaced the reply that just streamed with a safe version (#67). */
	public static StreamEvent corrected(Long messageId, String text) {
		return new StreamEvent("corrected", Map.of("messageId", messageId, "text", text));
	}

	/** The turn is over; {@code messageId} is the saved advisor message. */
	public static StreamEvent done(Long messageId) {
		return new StreamEvent("done", Map.of("messageId", messageId));
	}

	public static StreamEvent error(String message) {
		return new StreamEvent("error", Map.of("message", message));
	}
}
