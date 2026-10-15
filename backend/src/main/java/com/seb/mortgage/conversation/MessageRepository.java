package com.seb.mortgage.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByConversationIdOrderByIdAsc(UUID conversationId);
}
