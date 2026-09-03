package com.codegym.mathclass.storage.controller;

import com.codegym.mathclass.storage.dto.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.service.StorageCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminStorageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StorageCleanupService storageCleanupService;

    @InjectMocks
    private AdminStorageController adminStorageController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminStorageController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("POST /admin/storage/cleanup should trigger cleanup and return 200 OK")
    void triggerCleanup_Success() throws Exception {
        StorageCleanupResponse response = StorageCleanupResponse.builder()
                .scannedBuckets(List.of("avatar", "assignment_image"))
                .totalFilesScanned(100)
                .orphanFilesDetected(10)
                .filesDeletedSuccessfully(10)
                .failedDeletions(0)
                .executionTimeMs(500)
                .dryRun(false)
                .completedAt(LocalDateTime.now())
                .build();

        when(storageCleanupService.runCleanup(any())).thenReturn(response);

        StorageCleanupRequest request = StorageCleanupRequest.builder()
                .gracePeriodHours(24)
                .dryRun(false)
                .build();

        mockMvc.perform(post("/admin/storage/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFilesScanned").value(100))
                .andExpect(jsonPath("$.orphanFilesDetected").value(10))
                .andExpect(jsonPath("$.filesDeletedSuccessfully").value(10));
    }

    @Test
    @DisplayName("GET /admin/storage/cleanup/status should return status and 200 OK")
    void getCleanupStatus_Success() throws Exception {
        StorageCleanupStatusResponse statusResponse = StorageCleanupStatusResponse.builder()
                .enabled(true)
                .cronExpression("0 0 3 * * SUN")
                .gracePeriodHours(24)
                .build();

        when(storageCleanupService.getCleanupStatus()).thenReturn(statusResponse);

        mockMvc.perform(get("/admin/storage/cleanup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.cronExpression").value("0 0 3 * * SUN"))
                .andExpect(jsonPath("$.gracePeriodHours").value(24));
    }

    @Test
    @DisplayName("PUT /admin/storage/cleanup/config should update config and return 200 OK")
    void updateConfig_Success() throws Exception {
        com.codegym.mathclass.storage.dto.UpdateStorageCleanupConfigRequest request =
                com.codegym.mathclass.storage.dto.UpdateStorageCleanupConfigRequest.builder()
                        .enabled(false)
                        .cronExpression("0 0 2 * * *")
                        .gracePeriodHours(48)
                        .build();

        StorageCleanupStatusResponse statusResponse = StorageCleanupStatusResponse.builder()
                .enabled(false)
                .cronExpression("0 0 2 * * *")
                .gracePeriodHours(48)
                .build();

        when(storageCleanupService.updateConfig(any())).thenReturn(statusResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/storage/cleanup/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.cronExpression").value("0 0 2 * * *"))
                .andExpect(jsonPath("$.gracePeriodHours").value(48));
    }
}
