package com.codegym.mathclass.user.service;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.response.PermissionDto;
import com.codegym.mathclass.user.entity.Permission;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.RolePermission;
import com.codegym.mathclass.user.repository.PermissionRepository;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    private Permission permission1;
    private Permission permission2;
    private RolePermission rolePermission1;

    @BeforeEach
    void setUp() {
        permission1 = Permission.builder()
                .name("user:read")
                .description("Read user info")
                .build();
        permission1.setId(1L);

        permission2 = Permission.builder()
                .name("user:write")
                .description("Write user info")
                .build();
        permission2.setId(2L);

        rolePermission1 = RolePermission.builder()
                .role(Role.TEACHER)
                .permission(permission1)
                .build();
        rolePermission1.setId(10L);
    }

    @Nested
    @DisplayName("getPermissionsByRole Tests")
    class GetPermissionsByRoleTests {

        @Test
        @DisplayName("Should return permission DTOs for a given role")
        void getPermissionsByRole_ValidRole_ReturnsList() {
            when(rolePermissionRepository.findByRole(Role.TEACHER)).thenReturn(List.of(rolePermission1));

            List<PermissionDto> result = rolePermissionService.getPermissionsByRole(Role.TEACHER);

            assertThat(result).isNotNull().hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("user:read");
            verify(rolePermissionRepository, times(1)).findByRole(Role.TEACHER);
        }
    }

    @Nested
    @DisplayName("getAllPermissions Tests")
    class GetAllPermissionsTests {

        @Test
        @DisplayName("Should return all permissions in the system")
        void getAllPermissions_ReturnsList() {
            when(permissionRepository.findAll()).thenReturn(List.of(permission1, permission2));

            List<PermissionDto> result = rolePermissionService.getAllPermissions();

            assertThat(result).isNotNull().hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("user:read");
            assertThat(result.get(1).getName()).isEqualTo("user:write");
            verify(permissionRepository, times(1)).findAll();
        }
    }

    @Nested
    @DisplayName("updateRolePermissions Tests")
    class UpdateRolePermissionsTests {

        @Test
        @DisplayName("Should update role permissions successfully when valid permission IDs are provided")
        void updateRolePermissions_ValidIds_Success() {
            when(rolePermissionRepository.findByRole(Role.TEACHER)).thenReturn(List.of(rolePermission1));
            doNothing().when(rolePermissionRepository).deleteAll(anyList());
            doNothing().when(rolePermissionRepository).flush();
            when(permissionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(permission1, permission2));
            when(rolePermissionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

            rolePermissionService.updateRolePermissions(Role.TEACHER, List.of(1L, 2L));

            verify(rolePermissionRepository, times(1)).deleteAll(anyList());
            verify(rolePermissionRepository, times(1)).flush();
            verify(rolePermissionRepository, times(1)).saveAll(anyList());
        }

        @Test
        @DisplayName("Should clear all permissions when empty permissionIds list is provided")
        void updateRolePermissions_EmptyIds_ClearsPermissions() {
            when(rolePermissionRepository.findByRole(Role.TEACHER)).thenReturn(List.of(rolePermission1));
            doNothing().when(rolePermissionRepository).deleteAll(anyList());
            doNothing().when(rolePermissionRepository).flush();

            rolePermissionService.updateRolePermissions(Role.TEACHER, Collections.emptyList());

            verify(rolePermissionRepository, times(1)).deleteAll(anyList());
            verify(rolePermissionRepository, times(1)).flush();
            verify(rolePermissionRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should throw BadRequestException when some permission IDs do not exist in DB")
        void updateRolePermissions_InvalidIds_ThrowsException() {
            when(rolePermissionRepository.findByRole(Role.TEACHER)).thenReturn(List.of(rolePermission1));
            doNothing().when(rolePermissionRepository).deleteAll(anyList());
            doNothing().when(rolePermissionRepository).flush();
            when(permissionRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(permission1));

            assertThatThrownBy(() -> rolePermissionService.updateRolePermissions(Role.TEACHER, List.of(1L, 999L)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Lỗi dữ liệu: Có quyền truy cập không tồn tại.");

            verify(rolePermissionRepository, never()).saveAll(anyList());
        }
    }
}
