package com.codegym.mathclass.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phone);

    Boolean existsByEmail(String userName);

    Optional<User> findByVerificationCode(String verificationCode);

    List<User> findByRole(Role role);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :id")
    void updateLastActiveAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM Classroom c JOIN c.students s WHERE c.classCode = :classCode ORDER BY CASE WHEN s.lastActiveAt IS NULL THEN 1 ELSE 0 END, s.lastActiveAt DESC, s.fullName ASC")
    Page<User> findStudentsByClassCode(@Param("classCode") String classCode, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
        "(:role IS NULL OR u.role = :role) AND " +
        "(:isActive IS NULL OR u.isActive = :isActive) AND " +
        "(:search IS NULL OR LOWER(u.email) LIKE :search ESCAPE '\\' OR LOWER(u.fullName) LIKE :search ESCAPE '\\')")
     Page<User> findAllForAdmin(@Param("role") Role role,
                               @Param("isActive") Boolean isActive,
                               @Param("search") String search,
                               Pageable pageable);
}
