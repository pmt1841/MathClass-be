package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.exception.GlobalExceptionHandler;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.dto.request.UpdateRolePermissionsRequest;
import com.codegym.mathclass.user.dto.response.PermissionDto;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.RolePermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPermissionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private AdminPermissionController adminPermissionController;

    private ObjectMapper objectMapper;
    private UserDetails mockAdminDetails;
    private PermissionDto mockPermissionDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockAdminDetails = User.withUsername("admin@test.com")
                .password("password")
                .roles("ADMIN")
                .build();

        mockPermissionDto = PermissionDto.builder()
                .id(1L)
                .name("user:read")
                .description("Read user info")
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(adminPermissionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return UserDetails.class.isAssignableFrom(parameter.getParameterType());
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockAdminDetails;
                    }
                })
                .build();
    }

    @Nested
    @DisplayName("GET /api/admin/roles/permissions Integration Tests")
    class GetAllPermissionsEndpointTests {

        @Test
        @DisplayName("Should return all permissions list and 200 OK")
        void getAllPermissions_ReturnsOk() throws Exception {
            when(rolePermissionService.getAllPermissions()).thenReturn(List.of(mockPermissionDto));

            mockMvc.perform(get("/api/admin/roles/permissions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("user:read"));

            verify(rolePermissionService, times(1)).getAllPermissions();
        }
    }

    @Nested
    @DisplayName("GET /api/admin/roles/{roleName}/permissions Integration Tests")
    class GetPermissionsByRoleEndpointTests {

        @Test
        @DisplayName("Should return permissions for valid roleName and 200 OK")
        void getPermissionsByRole_ValidRole_ReturnsOk() throws Exception {
            when(rolePermissionService.getPermissionsByRole(Role.TEACHER)).thenReturn(List.of(mockPermissionDto));

            mockMvc.perform(get("/api/admin/roles/teacher/permissions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("user:read"));

            verify(rolePermissionService, times(1)).getPermissionsByRole(Role.TEACHER);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when roleName is invalid")
        void getPermissionsByRole_InvalidRole_Returns400BadRequest() throws Exception {
            mockMvc.perform(get("/api/admin/roles/invalid_role/permissions"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Role không hợp lệ: invalid_role"));

            verify(rolePermissionService, never()).getPermissionsByRole(any());
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/roles/{roleName}/permissions Integration Tests")
    class UpdateRolePermissionsEndpointTests {

        @Test
        @DisplayName("Should update role permissions and return 200 OK")
        void updateRolePermissions_ValidRequest_ReturnsOk() throws Exception {
            UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
            request.setPermissionIds(List.of(1L, 2L));

            doNothing().when(rolePermissionService).updateRolePermissions(eq(Role.TEACHER), eq(List.of(1L, 2L)));

            mockMvc.perform(put("/api/admin/roles/teacher/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Cập nhật phân quyền thành công."));

            verify(rolePermissionService, times(1)).updateRolePermissions(eq(Role.TEACHER), eq(List.of(1L, 2L)));
            verify(systemLogService, times(1)).logWarning(eq("admin@test.com"), anyString(), isNull());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when roleName is invalid")
        void updateRolePermissions_InvalidRole_Returns400BadRequest() throws Exception {
            UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
            request.setPermissionIds(List.of(1L, 2L));

            mockMvc.perform(put("/api/admin/roles/invalid_role/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Role không hợp lệ: invalid_role"));

            verify(rolePermissionService, never()).updateRolePermissions(any(), any());
        }
    }
}
