package com.seb.mortgage.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByUserId(UUID userId);

	Optional<Document> findByUserIdAndType(UUID userId, DocumentType type);

	Optional<Document> findByIdAndUserId(UUID id, UUID userId);

	void deleteByUserId(UUID userId);
}
