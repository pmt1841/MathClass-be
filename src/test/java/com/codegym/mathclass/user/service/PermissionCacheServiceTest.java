package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.RolePermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionCacheService permissionCacheService;

    @Nested
    @DisplayName("getPermissionsByRole Cache Tests")
    class GetPermissionsByRoleTests {

        @Test
        @DisplayName("Should query DB for permission names by role")
        void getPermissionsByRole_QueriesRepository() {
            when(rolePermissionRepository.findPermissionNamesByRole(Role.TEACHER))
                    .thenReturn(List.of("user:read", "classroom:create"));

            List<String> permissions = permissionCacheService.getPermissionsByRole(Role.TEACHER);

            assertThat(permissions).isNotNull().hasSize(2).contains("user:read", "classroom:create");
            verify(rolePermissionRepository, times(1)).findPermissionNamesByRole(Role.TEACHER);
        }
    }

    @Nested
    @DisplayName("evictPermissionsCache Tests")
    class EvictPermissionsCacheTests {

        @Test
        @DisplayName("Should execute evict permissions cache for a specific role without error")
        void evictPermissionsCache_ExecutesSuccessfully() {
            permissionCacheService.evictPermissionsCache(Role.TEACHER);
            // Method annotated with @CacheEvict executed cleanly
        }

        @Test
        @DisplayName("Should execute evict all permissions cache without error")
        void evictAllPermissionsCache_ExecutesSuccessfully() {
            permissionCacheService.evictAllPermissionsCache();
            // Method annotated with @CacheEvict(allEntries = true) executed cleanly
        }
    }
}
