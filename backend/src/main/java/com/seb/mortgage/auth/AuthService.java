package com.seb.mortgage.auth;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
	public record UserView(java.util.UUID id, String email) {}

	private final UserRepository users;
	private final PasswordEncoder passwords;

	public AuthService(UserRepository users, PasswordEncoder passwords) {
		this.users = users;
		this.passwords = passwords;
	}

	public UserView register(String email, String password) {
		String normalized = normalizeEmail(email);
		// Prototype: any non-empty password, so testing stays quick (#87).
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("password is required");
		}
		if (users.existsByEmail(normalized)) {
			throw new EmailAlreadyUsedException();
		}
		try {
			return view(users.save(new User(normalized, passwords.encode(password))));
		} catch (DataIntegrityViolationException e) {
			throw new EmailAlreadyUsedException();
		}
	}

	public UserView view(User user) { return new UserView(user.getId(), user.getEmail()); }
	public UserView view(java.util.UUID id, String email) { return new UserView(id, email); }
	private String normalizeEmail(String email) {
		if (email == null || !email.contains("@")) throw new IllegalArgumentException("valid email required");
		return email.trim().toLowerCase(Locale.ROOT);
	}
	public static class EmailAlreadyUsedException extends RuntimeException {}
}
