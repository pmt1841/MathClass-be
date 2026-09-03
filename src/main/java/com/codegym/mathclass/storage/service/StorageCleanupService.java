package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.storage.dto.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.dto.UpdateStorageCleanupConfigRequest;
import com.codegym.mathclass.storage.entity.StorageCleanupConfig;

public interface StorageCleanupService {

    StorageCleanupResponse runCleanup(StorageCleanupRequest request);

    StorageCleanupResponse runCleanup(int gracePeriodHours, boolean dryRun);

    StorageCleanupStatusResponse getCleanupStatus();

    StorageCleanupStatusResponse updateConfig(UpdateStorageCleanupConfigRequest request);

    StorageCleanupConfig getOrCreateConfig();
}
