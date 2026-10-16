package com.seb.mortgage.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {
	List<Email> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}