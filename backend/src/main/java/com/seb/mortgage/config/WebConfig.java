package com.seb.mortgage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Nuxt dev server (default http://localhost:3000) to call the API directly.
 * The allowed origin is configurable via {@code app.cors.allowed-origins} so it can be
 * tightened per environment.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}
}
