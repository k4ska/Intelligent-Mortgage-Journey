package com.seb.mortgage.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Stand-in for {@link CurrentUser} until auth (#49) provides the real one: every request
 * acts as the same demo user. It backs off automatically as soon as another
 * {@code CurrentUser} bean exists; #49 can then delete this class.
 */
@Configuration
public class DevCurrentUserConfig {

	static final UUID DEMO_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Bean
	@ConditionalOnMissingBean(CurrentUser.class)
	CurrentUser demoCurrentUser() {
		return () -> DEMO_USER;
	}
}
