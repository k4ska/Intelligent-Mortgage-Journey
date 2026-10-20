package com.seb.mortgage.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

	Optional<Property> findByUserId(UUID userId);

	void deleteByUserId(UUID userId);
}
