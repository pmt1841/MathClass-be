package com.codegym.mathclass.chat.repository;

import com.codegym.mathclass.chat.entity.GroupChatReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupChatReadStateRepository extends JpaRepository<GroupChatReadState, Long> {
    Optional<GroupChatReadState> findByClassIdAndUserId(Long classId, Long userId);
}
