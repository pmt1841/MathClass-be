package com.codegym.mathclass.chat.repository;

import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        SELECT new com.codegym.mathclass.chat.dto.ChatMessageResponse(
            m.id, 
            m.classId, 
            m.studentId, 
            m.sender.id, 
            m.sender.fullName, 
            m.sender.avatarUrl, 
            m.content, 
            m.isRead, 
            m.createdAt
        )
        FROM ChatMessage m 
        JOIN m.sender
        WHERE m.classId = :classId AND m.studentId = :studentId
        ORDER BY m.createdAt DESC
        """)
    Page<ChatMessageResponse> findHistoryByClassIdAndStudentId(
        @Param("classId") Long classId,
        @Param("studentId") Long studentId,
        Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE ChatMessage m 
        SET m.isRead = true 
        WHERE m.classId = :classId 
          AND m.sender.id = :targetStudentId 
          AND m.isRead = false
        """)
    int markMessagesAsRead(
        @Param("classId") Long classId, 
        @Param("targetStudentId") Long targetStudentId
    );

    @Query("""
        SELECT COUNT(m)
        FROM ChatMessage m
        WHERE m.classId = :classId
          AND m.studentId = :studentId
          AND m.sender.id != :currentUserId
          AND m.isRead = false
        """)
    long countUnreadMessages(
        @Param("classId") Long classId,
        @Param("studentId") Long studentId,
        @Param("currentUserId") Long currentUserId
    );

    @Query("""
        SELECT DISTINCT (CASE WHEN m.studentId IS NOT NULL THEN m.studentId ELSE m.sender.id END)
        FROM ChatMessage m
        WHERE m.classId = :classId
          AND m.sender.id != :currentUserId
          AND m.isRead = false
        """)
    java.util.List<Long> findUnreadStudentIds(
        @Param("classId") Long classId,
        @Param("currentUserId") Long currentUserId
    );

    @Query("""
        SELECT m FROM ChatMessage m 
        JOIN FETCH m.sender 
        WHERE m.classId = :classId 
          AND m.chatType = com.codegym.mathclass.chat.entity.ChatType.CLASS_GROUP
        ORDER BY m.createdAt DESC
        """)
    Page<ChatMessage> findGroupHistoryByClassId(
        @Param("classId") Long classId,
        Pageable pageable
    );

    @Query("""
        SELECT m FROM ChatMessage m 
        JOIN FETCH m.sender 
        WHERE m.classId = :classId 
          AND m.chatType = com.codegym.mathclass.chat.entity.ChatType.DIRECT_STUDENT
          AND (
            (m.sender.id = :user1Id AND m.recipientId = :user2Id) 
            OR (m.sender.id = :user2Id AND m.recipientId = :user1Id)
          )
        ORDER BY m.createdAt DESC
        """)
    Page<ChatMessage> findDirectHistoryByClassIdAndUsers(
        @Param("classId") Long classId,
        @Param("user1Id") Long user1Id,
        @Param("user2Id") Long user2Id,
        Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE ChatMessage m 
        SET m.isRead = true 
        WHERE m.classId = :classId 
          AND m.chatType = com.codegym.mathclass.chat.entity.ChatType.DIRECT_STUDENT
          AND m.sender.id = :otherUserId 
          AND m.recipientId = :currentUserId
          AND m.isRead = false
        """)
    int markDirectMessagesAsRead(
        @Param("classId") Long classId, 
        @Param("otherUserId") Long otherUserId, 
        @Param("currentUserId") Long currentUserId
    );
}
