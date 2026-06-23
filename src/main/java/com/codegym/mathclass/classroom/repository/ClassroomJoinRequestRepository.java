package com.codegym.mathclass.classroom.repository;

import com.codegym.mathclass.classroom.entity.ClassroomJoinRequest;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomJoinRequestRepository extends JpaRepository<ClassroomJoinRequest, Long> {
    
    List<ClassroomJoinRequest> findByClassroomIdAndStatus(Long classroomId, JoinRequestStatus status);
    
    List<ClassroomJoinRequest> findByClassroomClassCodeAndStatus(String classCode, JoinRequestStatus status);

    Optional<ClassroomJoinRequest> findByClassroomIdAndStudentIdAndStatus(Long classroomId, Long studentId, JoinRequestStatus status);
    
    List<ClassroomJoinRequest> findByStudentId(Long studentId);
}
