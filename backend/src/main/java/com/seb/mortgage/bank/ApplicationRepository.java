package com.seb.mortgage.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
	Optional<Application> findByIdAndUserId(UUID id, UUID userId);
}