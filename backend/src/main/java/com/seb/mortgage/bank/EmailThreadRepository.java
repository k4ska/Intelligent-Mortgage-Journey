package com.seb.mortgage.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailThreadRepository extends JpaRepository<EmailThread, UUID> {
	List<EmailThread> findByUserIdOrderByCreatedAtDesc(UUID userId);
	Optional<EmailThread> findByIdAndUserId(UUID id, UUID userId);
}