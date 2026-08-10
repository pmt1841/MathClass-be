package com.codegym.mathclass.aiconfig.repository;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByProviderId(Long providerId);
    List<ApiKey> findByProviderIdAndStatusOrderByPriorityDesc(Long providerId, ApiKeyStatus status);

    @Modifying
    @Query("UPDATE ApiKey k SET k.status = :status WHERE k.provider.id = :providerId")
    int updateStatusByProviderId(@Param("providerId") Long providerId, @Param("status") ApiKeyStatus status);
}
