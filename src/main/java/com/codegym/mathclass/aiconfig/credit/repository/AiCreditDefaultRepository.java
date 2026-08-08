package com.codegym.mathclass.aiconfig.credit.repository;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditDefault;
import com.codegym.mathclass.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiCreditDefaultRepository extends JpaRepository<AiCreditDefault, Long> {

    Optional<AiCreditDefault> findByRole(Role role);

    List<AiCreditDefault> findAllByOrderByRoleAsc();
}
