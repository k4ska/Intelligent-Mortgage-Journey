package com.seb.mortgage.profile;

/** A profile field as the API, the stream events and the bank snapshot show it. */
public record ProfileFieldView(String key, String value, ProfileField.Source source, ProfileField.Status status) {}
