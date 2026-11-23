package com.seb.mortgage.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActiveConversationRepository extends JpaRepository<ActiveConversation, UUID> {
}
