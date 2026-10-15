package com.seb.mortgage.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileFieldRepository extends JpaRepository<ProfileField, UUID> {

	List<ProfileField> findByUserId(UUID userId);

	Optional<ProfileField> findByUserIdAndKey(UUID userId, ProfileKey key);
}
