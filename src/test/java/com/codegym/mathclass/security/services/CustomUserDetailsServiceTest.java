package com.codegym.mathclass.security.services;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .fullName("Nguyen Van A")
                .email("nguyenvana@mathclass.edu.vn")
                .password("encoded-secret-password")
                .role(Role.TEACHER)
                .isActive(true)
                .avatarUrl("https://storage.mathclass.edu.vn/avatar.png")
                .build();
        sampleUser.setId(10L);
    }

    @Test
    @DisplayName("Tải thông tin người dùng thành công kèm Role và Permissions")
    void loadUserByUsername_Success() {
        when(userRepository.findByEmail("nguyenvana@mathclass.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(permissionCacheService.getPermissionsByRole(Role.TEACHER))
                .thenReturn(List.of("assignment:create", "classroom:manage"));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("nguyenvana@mathclass.edu.vn");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("nguyenvana@mathclass.edu.vn");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-secret-password");
        assertThat(userDetails.isEnabled()).isTrue();

        List<String> authorityStrings = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorityStrings).containsExactlyInAnyOrder(
                "ROLE_TEACHER",
                "assignment:create",
                "classroom:manage"
        );

        assertThat(userDetails).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
        assertThat(customUserDetails.getId()).isEqualTo(10L);
        assertThat(customUserDetails.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(customUserDetails.getAvatarUrl()).isEqualTo("https://storage.mathclass.edu.vn/avatar.png");
    }

    @Test
    @DisplayName("Ném UsernameNotFoundException khi email không tồn tại trong cơ sở dữ liệu")
    void loadUserByUsername_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail("notfound@mathclass.edu.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("notfound@mathclass.edu.vn"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tài khoản: notfound@mathclass.edu.vn");
    }

    @Test
    @DisplayName("Tải thông tin người dùng khi danh sách permissions rỗng chỉ chứa ROLE authority")
    void loadUserByUsername_EmptyPermissions_ContainsOnlyRole() {
        sampleUser.setRole(Role.STUDENT);
        when(userRepository.findByEmail("student@mathclass.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(permissionCacheService.getPermissionsByRole(Role.STUDENT)).thenReturn(Collections.emptyList());

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("student@mathclass.edu.vn");

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("Tải thông tin người dùng khi permissions là null vẫn an toàn và chứa ROLE authority")
    void loadUserByUsername_NullPermissions_ContainsOnlyRole() {
        sampleUser.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@mathclass.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(permissionCacheService.getPermissionsByRole(Role.ADMIN)).thenReturn(null);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin@mathclass.edu.vn");

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }
}
