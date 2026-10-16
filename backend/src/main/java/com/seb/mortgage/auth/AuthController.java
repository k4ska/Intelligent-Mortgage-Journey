package com.seb.mortgage.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	public record Credentials(String email, String password) {}

	private final AuthService auth;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	public AuthController(AuthService auth, AuthenticationManager authenticationManager) {
		this.auth = auth;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthService.UserView register(@RequestBody Credentials credentials) {
		return auth.register(credentials.email(), credentials.password());
	}

	@PostMapping("/login")
	public AuthService.UserView login(@RequestBody Credentials credentials, HttpServletRequest request,
			HttpServletResponse response) {
		var authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(credentials.email(), credentials.password()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		contextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
		return auth.view(((AuthUserDetails) authentication.getPrincipal()).id(), ((AuthUserDetails) authentication.getPrincipal()).email());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
	}

	@GetMapping("/me")
	public AuthService.UserView me(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthUserDetails user) {
		return auth.view(user.id(), user.email());
	}

	@ExceptionHandler(AuthService.EmailAlreadyUsedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public void duplicateEmail() {}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public void invalidCredentials() {}
}