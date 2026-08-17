package com.codegym.mathclass.bugreport.service;

import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.SendOtpRequest;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.bugreport.service.impl.BugReportServiceImpl;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.TooManyRequestsException;
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
        studentUser.setEmail("student@gmail.com");
        studentUser.setFullName("Student Test");
        studentUser.setRole(Role.STUDENT);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@gmail.com");
        adminUser.setRole(Role.ADMIN);

        bugReport = BugReport.builder()
                .reporterEmail("guest@gmail.com")
                .reporterName("Guest User")
                .errorType(BugErrorType.LOGIN_ACCOUNT)
                .description("Can not login")
                .status(BugReportStatus.PENDING)
                .build();
        bugReport.setId(100L);
    }

    @Nested
    @DisplayName("sendPublicReportOtp Tests")
    class SendPublicReportOtpTests {

        @Test
        @DisplayName("Should send OTP successfully for valid email domain")
        void sendPublicReportOtp_ValidEmail_Success() {
            SendOtpRequest request = new SendOtpRequest("guest@gmail.com");

            bugReportService.sendPublicReportOtp(request, "127.0.0.1");

            verify(emailService, times(1)).sendBugReportOtpEmail(eq("guest@gmail.com"), anyString());
        }

        @Test
        @DisplayName("Should throw BadRequestException for invalid fake domain")
        void sendPublicReportOtp_FakeDomain_ThrowsException() {
            SendOtpRequest request = new SendOtpRequest("user@domain-khong-ton-tai-123456.xyz");

            assertThatThrownBy(() -> bugReportService.sendPublicReportOtp(request, "127.0.0.1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không tồn tại hoặc không thể nhận thư");

            verify(emailService, never()).sendBugReportOtpEmail(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("createPublicReport Anti-Bot Tests")
    class CreatePublicReportAntiBotTests {

        @Test
        @DisplayName("Honeypot Trap: Should return fake 200 success response when website field is filled by spambot")
        void createPublicReport_HoneypotTrap_ReturnsFakeSuccess() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .reporterEmail("bot@gmail.com")
                    .errorType(BugErrorType.LOGIN_ACCOUNT)
                    .website("http://spam-link.com") // Spambot filled the hidden honeypot field!
                    .build();

            BugReportResponse response = bugReportService.createPublicReport(request, "192.168.1.10");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(0L); // Fake report ID 0
            verify(bugReportRepository, never()).save(any()); // NOT saved to DB
        }

        @Test
        @DisplayName("Time-based Check: Should throw BadRequestException when submitted under 3 seconds")
        void createPublicReport_SubmittedTooFast_ThrowsBadRequestException() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .reporterEmail("user@gmail.com")
                    .errorType(BugErrorType.LOGIN_ACCOUNT)
                    .formLoadedAt(System.currentTimeMillis() - 1000) // Submitted in 1 second!
                    .build();

            assertThatThrownBy(() -> bugReportService.createPublicReport(request, "192.168.1.11"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Thao tác gửi báo cáo quá nhanh");

            verify(bugReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when OTP is missing")
        void createPublicReport_MissingOtp_ThrowsBadRequestException() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .reporterEmail("missing-otp-test@gmail.com")
                    .reporterName("Guest User")
                    .errorType(BugErrorType.LOGIN_ACCOUNT)
                    .description("Lỗi đăng nhập")
                    .formLoadedAt(System.currentTimeMillis() - 5000) // 5s ok
                    .build();

            assertThatThrownBy(() -> bugReportService.createPublicReport(request, "10.0.0.99"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Vui lòng nhập mã OTP");

            verify(bugReportRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("createAuthenticatedReport Tests")
    class CreateAuthenticatedReportTests {

        @Test
        @DisplayName("Should create report for authenticated user without OTP requirement")
        void createAuthenticatedReport_ValidUser_Success() {
            CreateBugReportRequest request = CreateBugReportRequest.builder()
                    .errorType(BugErrorType.UI_KATEX)
                    .description("KaTeX error")
                    .build();

            when(userRepository.findByEmail("student@gmail.com")).thenReturn(Optional.of(studentUser));
            when(bugReportRepository.save(any(BugReport.class))).thenReturn(bugReport);
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

            BugReportResponse response = bugReportService.createAuthenticatedReport(request, "student@gmail.com", "127.0.0.1");

            assertThat(response).isNotNull();
            verify(bugReportRepository, times(1)).save(any(BugReport.class));
        }
    }
}
