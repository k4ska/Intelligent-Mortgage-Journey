package com.seb.mortgage.auth;

import java.util.UUID;

/**
 * Who is making the current request. Everything the advisor and the banks store is keyed
 * by this id.
 *
 * <p>{@link SessionCurrentUser} provides it from the logged-in session.
 */
public interface CurrentUser {

	UUID id();
}
