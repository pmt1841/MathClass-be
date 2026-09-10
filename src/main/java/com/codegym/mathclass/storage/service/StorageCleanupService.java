package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.storage.dto.request.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.response.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.response.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.dto.request.UpdateStorageCleanupConfigRequest;

public interface StorageCleanupService {

    StorageCleanupResponse runCleanup(StorageCleanupRequest request);

    StorageCleanupResponse runCleanup(int gracePeriodHours, boolean dryRun);

    StorageCleanupStatusResponse getCleanupStatus();

    StorageCleanupStatusResponse updateConfig(UpdateStorageCleanupConfigRequest request);
}
