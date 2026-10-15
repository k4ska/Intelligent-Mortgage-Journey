package com.seb.mortgage.auth;

import java.util.UUID;

/**
 * Who is making the current request. Everything the advisor and the banks store is keyed
 * by this id.
 *
 * <p>Auth (#49) provides the real implementation from the logged-in session. Until it
 * lands, {@link DevCurrentUserConfig} stands in with one fixed demo user, so the advisor
 * side can be built and tested without login.
 */
public interface CurrentUser {

	UUID id();
}
