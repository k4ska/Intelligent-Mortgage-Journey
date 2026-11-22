package com.seb.mortgage.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(UUID conversationId);

    Optional<Message> findFirstByConversationIdAndRoleAndThreadIdIsNullOrderByIdAsc(
            UUID conversationId, Message.Role role);

    void deleteByConversationId(UUID conversationId);
}