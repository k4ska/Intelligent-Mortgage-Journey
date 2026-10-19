package com.seb.mortgage.profile;

import com.seb.mortgage.auth.CurrentUser;
import com.seb.mortgage.property.PropertyService;
import com.seb.mortgage.property.PropertyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

	/** {@code property} is null until the user has a home in mind. */
	public record ProfileResponse(List<ProfileFieldView> fields, PropertyView property) {}

	private final ProfileService profile;
	private final PropertyService property;
	private final CurrentUser currentUser;

	public ProfileController(ProfileService profile, PropertyService property, CurrentUser currentUser) {
		this.profile = profile;
		this.property = property;
		this.currentUser = currentUser;
	}

	@GetMapping
	public ProfileResponse get() {
		UUID userId = currentUser.id();
		return new ProfileResponse(profile.fields(userId), property.active(userId).orElse(null));
	}
}
