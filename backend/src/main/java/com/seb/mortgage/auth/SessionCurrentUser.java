package com.seb.mortgage.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SessionCurrentUser implements CurrentUser {
	@Override
	public UUID id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails user)) {
			throw new IllegalStateException("No authenticated user");
		}
		return user.id();
	}
}