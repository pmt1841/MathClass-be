package com.codegym.mathclass.bugreport.service;

import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.bugreport.service.impl.BugReportServiceImpl;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugReportServiceImplTest {

    @Mock
    private BugReportRepository bugReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BugReportServiceImpl bugReportService;

    private User studentUser;
    private User adminUser;
    private BugReport bugReport;

    @BeforeEach
    void setUp() {
        studentUser = new User();
        studentUser.setId(10L);
        studentUser.setEmail("student@mathclass.com");
        studentUser.setFullName("Student Test");
        studentUser.setRole(Role.STUDENT);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@mathclass.com");
        adminUser.setRole(Role.ADMIN);

        bugReport = BugReport.builder()
                .reporterEmail("guest@mathclass.com")
                .reporterName("Guest User")
                .errorType(BugErrorType.LOGIN_ACCOUNT)
                .description("Can not login")
                .status(BugReportStatus.PENDING)
                .build();
        bugReport.setId(100L);
    }

    @Nested
    @DisplayName("createPublicReport Tests")
    class CreatePublicReportTests {

        @Test
        @DisplayName("Should create public report and notify admins")
        void createPublicReport_ValidData_Success() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .reporterEmail("guest@mathclass.com")
                    .reporterName("Guest User")
                    .errorType(BugErrorType.LOGIN_ACCOUNT)
                    .description("Lỗi đăng nhập")
                    .build();

            when(bugReportRepository.save(any(BugReport.class))).thenReturn(bugReport);
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

            BugReportResponse response = bugReportService.createPublicReport(request);

            assertThat(response).isNotNull();
            assertThat(response.getReporterEmail()).isEqualTo("guest@mathclass.com");
            verify(bugReportRepository, times(1)).save(any(BugReport.class));
            verify(notificationService, times(1)).saveAndSendNotification(eq(1L), anyString(), eq("/admin/bug-reports"));
        }

        @Test
        @DisplayName("Should throw BadRequestException when email is empty")
        void createPublicReport_EmptyEmail_ThrowsBadRequestException() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .reporterEmail("  ")
                    .errorType(BugErrorType.LOGIN_ACCOUNT)
                    .build();

            assertThatThrownBy(() -> bugReportService.createPublicReport(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email không được để trống");

            verify(bugReportRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("createAuthenticatedReport Tests")
    class CreateAuthenticatedReportTests {

        @Test
        @DisplayName("Should create report for authenticated user and notify admins")
        void createAuthenticatedReport_ValidUser_Success() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .errorType(BugErrorType.UI_KATEX)
                    .description("KaTeX error")
                    .build();

            when(userRepository.findByEmail("student@mathclass.com")).thenReturn(Optional.of(studentUser));
            when(bugReportRepository.save(any(BugReport.class))).thenReturn(bugReport);
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

            BugReportResponse response = bugReportService.createAuthenticatedReport(request, "student@mathclass.com");

            assertThat(response).isNotNull();
            verify(bugReportRepository, times(1)).save(any(BugReport.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void createAuthenticatedReport_UserNotFound_ThrowsException() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .errorType(BugErrorType.UI_KATEX)
                    .build();

            when(userRepository.findByEmail("unknown@mathclass.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bugReportService.createAuthenticatedReport(request, "unknown@mathclass.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getReports Tests")
    class GetReportsTests {

        @Test
        @DisplayName("Should return paginated reports")
        void getReports_AllStatus_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<BugReport> page = new PageImpl<>(Collections.singletonList(bugReport));
            when(bugReportRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

            Page<BugReportResponse> result = bugReportService.getReports(null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return paginated reports filtered by status")
        void getReports_FilterByStatus_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<BugReport> page = new PageImpl<>(Collections.singletonList(bugReport));
            when(bugReportRepository.findByStatus(BugReportStatus.PENDING, pageable)).thenReturn(page);

            Page<BugReportResponse> result = bugReportService.getReports(BugReportStatus.PENDING, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateReportStatus Tests")
    class UpdateReportStatusTests {

        @Test
        @DisplayName("Should update report status and trigger email & notification when status changes")
        void updateReportStatus_StatusChanged_Success() {
            bugReport.setUserId(10L);
            UpdateBugReportStatusRequest request = new UpdateBugReportStatusRequest();
            request.setStatus(BugReportStatus.RESOLVED);

            when(bugReportRepository.findById(100L)).thenReturn(Optional.of(bugReport));
            when(bugReportRepository.save(any(BugReport.class))).thenReturn(bugReport);

            BugReportResponse response = bugReportService.updateReportStatus(100L, request);

            assertThat(response).isNotNull();
            verify(emailService, times(1)).sendBugReportStatusEmail(anyString(), any(), anyString(), anyString(), anyString());
            verify(notificationService, times(1)).saveAndSendNotification(eq(10L), anyString(), eq("/home"));
        }
    }
}
