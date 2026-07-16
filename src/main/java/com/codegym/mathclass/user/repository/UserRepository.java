package com.codegym.mathclass.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.codegym.mathclass.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phone);

    Boolean existsByEmail(String userName);

    Optional<User> findByVerificationCode(String verificationCode);


    @Query("SELECT s FROM Classroom c JOIN c.students s WHERE c.classCode = :classCode")
    Page<User> findStudentsByClassCode(@Param("classCode") String classCode, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:isActive IS NULL OR u.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAllForAdmin(@Param("role") com.codegym.mathclass.user.entity.Role role,
                               @Param("isActive") Boolean isActive,
                               @Param("search") String search,
                               Pageable pageable);
}
