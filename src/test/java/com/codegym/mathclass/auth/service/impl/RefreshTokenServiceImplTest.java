package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.repository.RefreshTokenRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User mockUser;
    private RefreshToken mockToken;
    private final Long EXPIRATION_MS = 86400000L; // 24h

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", EXPIRATION_MS);

        mockUser = User.builder()
                .email("student@test.com")
                .fullName("Test Student")
                .role(Role.STUDENT)
                .build();
        mockUser.setId(1L);

        mockToken = RefreshToken.builder()
                .id(100L)
                .user(mockUser)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(EXPIRATION_MS))
                .build();
    }

    @Nested
    @DisplayName("findByToken Tests")
    class FindByTokenTests {

        @Test
        @DisplayName("Should return RefreshToken when found by token string")
        void findByToken_ExistingToken_ReturnsOptionalToken() {
            String tokenStr = mockToken.getToken();
            when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(mockToken));

            Optional<RefreshToken> result = refreshTokenService.findByToken(tokenStr);

            assertThat(result).isPresent();
            assertThat(result.get().getToken()).isEqualTo(tokenStr);
            verify(refreshTokenRepository, times(1)).findByToken(tokenStr);
        }

        @Test
        @DisplayName("Should return Empty Optional when token string is not found")
        void findByToken_NonExistingToken_ReturnsEmptyOptional() {
            when(refreshTokenRepository.findByToken("non-existing")).thenReturn(Optional.empty());

            Optional<RefreshToken> result = refreshTokenService.findByToken("non-existing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createRefreshToken Tests")
    class CreateRefreshTokenTests {

        @Test
        @DisplayName("Should create and save new RefreshToken when user exists and tokens < 3")
        void createRefreshToken_ValidUser_Success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(refreshTokenRepository.findAllByUserOrderByExpiryDateAsc(mockUser)).thenReturn(new ArrayList<>());
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RefreshToken createdToken = refreshTokenService.createRefreshToken(1L);

            assertThat(createdToken).isNotNull();
            assertThat(createdToken.getUser()).isEqualTo(mockUser);
            assertThat(createdToken.getToken()).isNotBlank();
            assertThat(createdToken.getExpiryDate()).isAfter(Instant.now());

            verify(refreshTokenRepository, never()).delete(any());
            verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should delete oldest token when user already has 3 or more active tokens")
        void createRefreshToken_MaxDeviceLimitReached_DeletesOldestToken() {
            List<RefreshToken> existingTokens = new ArrayList<>();
            for (long i = 1; i <= 3; i++) {
                existingTokens.add(RefreshToken.builder()
                        .id(i)
                        .user(mockUser)
                        .token("token-" + i)
                        .expiryDate(Instant.now().plusSeconds(i * 100))
                        .build());
            }

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(refreshTokenRepository.findAllByUserOrderByExpiryDateAsc(mockUser)).thenReturn(existingTokens);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RefreshToken createdToken = refreshTokenService.createRefreshToken(1L);

            assertThat(createdToken).isNotNull();
            verify(refreshTokenRepository, times(1)).delete(existingTokens.get(0));
            verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should throw BadRequestException when user is not found")
        void createRefreshToken_UserNotFound_ThrowsBadRequestException() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.createRefreshToken(99L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy User");

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("verifyExpiration Tests")
    class VerifyExpirationTests {

        @Test
        @DisplayName("Should return token when token has not expired")
        void verifyExpiration_ValidNonExpiredToken_ReturnsToken() {
            RefreshToken result = refreshTokenService.verifyExpiration(mockToken);

            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo(mockToken.getToken());
            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should delete token and throw BadRequestException when token has expired")
        void verifyExpiration_ExpiredToken_DeletesTokenAndThrowsException() {
            mockToken.setExpiryDate(Instant.now().minusSeconds(3600));

            assertThatThrownBy(() -> refreshTokenService.verifyExpiration(mockToken))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Refresh token đã hết hạn");

            verify(refreshTokenRepository, times(1)).delete(mockToken);
        }
    }

    @Nested
    @DisplayName("deleteByUserId Tests")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("Should delete tokens by user id and return count")
        void deleteByUserId_ValidUser_ReturnsDeletedCount() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(refreshTokenRepository.deleteByUser(mockUser)).thenReturn(2);

            int deletedCount = refreshTokenService.deleteByUserId(1L);

            assertThat(deletedCount).isEqualTo(2);
            verify(refreshTokenRepository, times(1)).deleteByUser(mockUser);
        }

        @Test
        @DisplayName("Should throw BadRequestException when user id is invalid")
        void deleteByUserId_UserNotFound_ThrowsBadRequestException() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.deleteByUserId(99L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không tìm thấy User");

            verify(refreshTokenRepository, never()).deleteByUser(any());
        }
    }
}
