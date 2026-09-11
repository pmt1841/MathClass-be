package com.codegym.mathclass.systemlog.service.impl;

import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLog;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceImplTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @InjectMocks
    private SystemLogServiceImpl systemLogService;

    private SystemLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = SystemLog.builder()
                .actor("admin@mathclass.edu.vn")
                .action("UPDATE_AI_KEY")
                .level(SystemLogLevel.INFO)
                .resourceType("AI_CONFIG")
                .resourceId("1")
                .ipAddress("192.168.1.1")
                .userAgent("Chrome")
                .status("SUCCESS")
                .build();
        sampleLog.setId(10L);
    }

    @Nested
    @DisplayName("getLogs Query Tests")
    class GetLogsTests {

        @Test
        @DisplayName("Should query and map system logs with specification filters")
        void getLogs_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<SystemLog> page = new PageImpl<>(List.of(sampleLog), pageable, 1);

            when(systemLogRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<SystemLogResponse> result = systemLogService.getLogs(
                    SystemLogLevel.INFO, "AI_CONFIG", "admin",
                    LocalDateTime.now().minusDays(1), LocalDateTime.now(), pageable
            );

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            SystemLogResponse dto = result.getContent().get(0);
            assertEquals(10L, dto.getId());
            assertEquals("admin@mathclass.edu.vn", dto.getActor());
            assertEquals("UPDATE_AI_KEY", dto.getAction());
            assertEquals(SystemLogLevel.INFO, dto.getLevel());
            assertEquals("SUCCESS", dto.getStatus());

            verify(systemLogRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("Logging Method Tests")
    class LogMethodsTests {

        @Test
        @DisplayName("log() should persist SystemLog entity with correct attributes")
        void log_CustomAttributes_Saved() {
            systemLogService.log(
                    "user@mathclass.edu.vn", "LOGIN", SystemLogLevel.INFO,
                    "AUTH", "100", "127.0.0.1", "Mozilla", "SUCCESS"
            );

            ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
            verify(systemLogRepository).save(captor.capture());

            SystemLog saved = captor.getValue();
            assertEquals("user@mathclass.edu.vn", saved.getActor());
            assertEquals("LOGIN", saved.getAction());
            assertEquals(SystemLogLevel.INFO, saved.getLevel());
            assertEquals("AUTH", saved.getResourceType());
            assertEquals("100", saved.getResourceId());
            assertEquals("SUCCESS", saved.getStatus());
        }

        @Test
        @DisplayName("log() should fallback to default values when nullable fields are omitted")
        void log_DefaultFallback() {
            systemLogService.log(null, "BACKGROUND_JOB", null, null, null, null, null, null);

            ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
            verify(systemLogRepository).save(captor.capture());

            SystemLog saved = captor.getValue();
            assertEquals("System", saved.getActor());
            assertEquals(SystemLogLevel.INFO, saved.getLevel());
            assertEquals("SUCCESS", saved.getStatus());
        }

        @Test
        @DisplayName("logInfo helper methods should record INFO level")
        void logInfo_Helpers() {
            systemLogService.logInfo("teacher", "CREATE_ASSIGNMENT", 15L);
            systemLogService.logInfo("teacher", "PUBLISH_SHEET", "SHEET", "20");

            verify(systemLogRepository, times(2)).save(any(SystemLog.class));
        }

        @Test
        @DisplayName("logWarning and logError helper methods should record appropriate level and status")
        void logWarningAndError_Helpers() {
            systemLogService.logWarning("admin", "PASSWORD_MISMATCH", 5L);
            systemLogService.logWarning("admin", "STORAGE_EXCEEDED", "STORAGE", "10");

            systemLogService.logError("user", "AI_EXECUTION_FAILED", 3L);
            systemLogService.logError("user", "PAYMENT_FAILED", "PAYMENT", "ORDER-99");

            verify(systemLogRepository, times(4)).save(any(SystemLog.class));
        }
    }
}
