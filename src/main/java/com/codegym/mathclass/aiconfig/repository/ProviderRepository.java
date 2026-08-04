package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByCode(String code);
    boolean existsByCode(String code);
}
