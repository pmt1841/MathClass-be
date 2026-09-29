package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AiCreditService aiCreditService;

    @InjectMocks
    private UserRegistrationServiceImpl registrationService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(registrationService, "frontendUrl", "http://localhost:3000");

        mockUser = User.builder()
                .email("student@test.com")
                .fullName("Test Student")
                .password("encodedPassword")
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        mockUser.setId(1L);
    }

    @Nested
    @DisplayName("registerUser Tests")
    class RegisterUserTests {

        @Test
        @DisplayName("Should successfully register a new student user and trigger verification email")
        void registerUser_ValidStudent_Success() {
            SignupRequest request = new SignupRequest();
            request.setEmail("student@test.com");
            request.setPassword("Password123!");
            request.setFullName("Test Student");
            request.setPhoneNumber("0987654321");
            request.setRole(Role.STUDENT);

            when(userRepository.existsByEmail("student@test.com")).thenReturn(false);
            when(encoder.encode("Password123!")).thenReturn("encodedPassword");
            doAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            }).when(userRepository).save(any(User.class));

            MessageResponse response = registrationService.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("Đăng ký tài khoản thành công");
            verify(userRepository, times(1)).save(any(User.class));
            verify(aiCreditService, times(1)).grantDefaultForNewUser(eq(1L), eq(Role.STUDENT));
            verify(notificationSettingsRepository, times(1)).save(any());
            verify(emailService, times(1)).sendHtmlMailAsync(eq("student@test.com"),
                    eq("Xác nhận đăng ký tài khoản MathClass"), eq("auth-verify"), any(Context.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException if email already exists")
        void registerUser_DuplicateEmail_ThrowsException() {
            SignupRequest request = new SignupRequest();
            request.setEmail("student@test.com");

            when(userRepository.existsByEmail("student@test.com")).thenReturn(true);

            assertThatThrownBy(() -> registrationService.registerUser(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email đã tồn tại");
        }

        @Test
        @DisplayName("Should throw BadRequestException if requested role is ADMIN")
        void registerUser_AdminRoleRequest_ThrowsException() {
            SignupRequest request = new SignupRequest();
            request.setEmail("hacker@test.com");
            request.setRole(Role.ADMIN);

            when(userRepository.existsByEmail("hacker@test.com")).thenReturn(false);

            assertThatThrownBy(() -> registrationService.registerUser(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Lỗi đăng ký tài khoản");
        }
    }

    @Nested
    @DisplayName("verifyUser Tests")
    class VerifyUserTests {

        @Test
        @DisplayName("Should activate user, clear code, and send welcome email on valid token")
        void verifyUser_ValidToken_Success() {
            User unverifiedUser = User.builder()
                    .email("unverified@test.com")
                    .fullName("Unverified Student")
                    .role(Role.STUDENT)
                    .isActive(false)
                    .verificationCode("valid-token")
                    .build();
            unverifiedUser.setId(2L);

            when(userRepository.findByVerificationCode("valid-token")).thenReturn(Optional.of(unverifiedUser));

            MessageResponse response = registrationService.verifyUser("valid-token");

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).contains("đã được kích hoạt thành công");
            assertThat(unverifiedUser.isActive()).isTrue();
            assertThat(unverifiedUser.getVerificationCode()).isNull();
            verify(userRepository, times(1)).save(unverifiedUser);
            verify(emailService, times(1)).sendHtmlMailAsync(eq("unverified@test.com"),
                    eq("Kích hoạt tài khoản thành công"), eq("auth-welcome"), any(Context.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException on invalid or non-existent token")
        void verifyUser_InvalidToken_ThrowsException() {
            when(userRepository.findByVerificationCode("fake-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> registrationService.verifyUser("fake-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã xác nhận không hợp lệ");
        }
    }

    @Nested
    @DisplayName("registerOAuth2User Tests")
    class RegisterOAuth2UserTests {

        @Test
        @DisplayName("Should successfully register OAuth2 user with credits and notification settings")
        void registerOAuth2User_Success() {
            String email = "oauth@gmail.com";
            String fullName = "OAuth User";
            String pictureUrl = "https://avatar.com/pic.jpg";
            Role role = Role.STUDENT;

            doAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(10L);
                return u;
            }).when(userRepository).save(any(User.class));

            User result = registrationService.registerOAuth2User(email, fullName, pictureUrl, role);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(result.getFullName()).isEqualTo(fullName);
            assertThat(result.getAvatarUrl()).isEqualTo(pictureUrl);
            assertThat(result.getRole()).isEqualTo(Role.STUDENT);
            assertThat(result.isActive()).isTrue();
            assertThat(result.getProvider()).isEqualTo(com.codegym.mathclass.user.entity.Provider.GOOGLE);

            verify(userRepository, times(1)).save(any(User.class));
            verify(aiCreditService, times(1)).grantDefaultForNewUser(eq(10L), eq(Role.STUDENT));
            verify(notificationSettingsRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should default role to STUDENT when role is null")
        void registerOAuth2User_NullRole_DefaultsToStudent() {
            doAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(11L);
                return u;
            }).when(userRepository).save(any(User.class));

            User result = registrationService.registerOAuth2User("oauth2@gmail.com", "OAuth 2", null, null);

            assertThat(result.getRole()).isEqualTo(Role.STUDENT);
            verify(aiCreditService).grantDefaultForNewUser(eq(11L), eq(Role.STUDENT));
        }
    }
}
