package com.codegym.mathclass.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codegym.mathclass.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phone);

    Boolean existsByEmail(String userName);

    Optional<User> findByVerificationCode(String verificationCode);
}
