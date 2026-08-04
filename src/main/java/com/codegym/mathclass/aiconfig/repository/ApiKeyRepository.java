package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByProviderId(Long providerId);
    List<ApiKey> findByProviderIdAndStatusOrderByPriorityDesc(Long providerId, ApiKeyStatus status);
}
