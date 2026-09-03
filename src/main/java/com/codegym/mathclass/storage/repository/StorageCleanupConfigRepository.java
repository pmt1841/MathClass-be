package com.codegym.mathclass.storage.repository;

import com.codegym.mathclass.storage.entity.StorageCleanupConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageCleanupConfigRepository extends JpaRepository<StorageCleanupConfig, Long> {
}
