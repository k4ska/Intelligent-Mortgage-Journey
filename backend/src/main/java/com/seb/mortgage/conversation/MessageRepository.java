package com.seb.mortgage.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

	/** A chat's messages plus the user's bank-thread messages from all their chats, oldest first (#160). */
	@Query("""
			select m from Message m
			where m.conversationId = :conversationId
				or (m.threadId is not null
					and m.conversationId in (select c.id from Conversation c where c.userId = :userId))
			order by m.id""")
	List<Message> findChatWithThreadMessages(@Param("conversationId") UUID conversationId,
			@Param("userId") UUID userId);

	Optional<Message> findFirstByConversationIdAndRoleAndThreadIdIsNullOrderByIdAsc(
			UUID conversationId, Message.Role role);

	long countByConversationId(UUID conversationId);

	void deleteByConversationId(UUID conversationId);
}
