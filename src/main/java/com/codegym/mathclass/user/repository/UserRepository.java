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
}
