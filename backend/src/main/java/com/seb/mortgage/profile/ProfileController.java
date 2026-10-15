package com.seb.mortgage.profile;

import com.seb.mortgage.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

	/** {@code property} stays null until listings are added. */
	public record ProfileResponse(List<ProfileFieldView> fields, Object property) {}

	private final ProfileService profile;
	private final CurrentUser currentUser;

	public ProfileController(ProfileService profile, CurrentUser currentUser) {
		this.profile = profile;
		this.currentUser = currentUser;
	}

	@GetMapping
	public ProfileResponse get() {
		return new ProfileResponse(profile.fields(currentUser.id()), null);
	}
}
