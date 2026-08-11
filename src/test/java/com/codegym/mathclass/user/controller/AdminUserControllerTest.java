package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.user.dto.request.UpdateUserStatusRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.service.AdminUserService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminUserController adminUserController;

    private UserDetails mockAdminDetails;
    private UserResponse mockUserResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Spring Data Page cần Jackson module để serialize đúng trong standalone MockMvc.
        // Dùng SpringDataWebAutoConfiguration PageModule để handle PageImpl.
        com.fasterxml.jackson.databind.module.SimpleModule pageModule =
            new com.fasterxml.jackson.databind.module.SimpleModule();
        pageModule.addSerializer(PageImpl.class, new com.fasterxml.jackson.databind.ser.std.StdSerializer<PageImpl>(PageImpl.class) {
            @Override
            public void serialize(PageImpl page, com.fasterxml.jackson.core.JsonGenerator gen,
                    com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
                gen.writeStartObject();
                gen.writeObjectField("content", page.getContent());
                gen.writeNumberField("totalElements", page.getTotalElements());
                gen.writeNumberField("totalPages", page.getTotalPages());
                gen.writeNumberField("size", page.getSize());
                gen.writeNumberField("number", page.getNumber());
                gen.writeBooleanField("first", page.isFirst());
                gen.writeBooleanField("last", page.isLast());
                gen.writeEndObject();
            }
        });
        objectMapper.registerModule(pageModule);

        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        mockAdminDetails = User.withUsername("admin@test.com")
                .password("password")
                .roles("ADMIN")
                .build();

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setFullName("Student A");
        mockUserResponse.setEmail("student@test.com");
        mockUserResponse.setRole(Role.STUDENT);
        mockUserResponse.setActive(true);

        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setMessageConverters(converter)
                .setCustomArgumentResolvers(
                    new PageableHandlerMethodArgumentResolver(),
                    new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(MethodParameter parameter) {
                            return UserDetails.class.isAssignableFrom(parameter.getParameterType());
                        }
                        @Override
                        public Object resolveArgument(MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
                            return mockAdminDetails;
                        }
                    }
                )
                .build();
    }

    // ==========================================
    // Tests for GET /api/admin/users
    // ==========================================

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetUsers {

        @Test
        @DisplayName("Should return paginated list of users with no filters")
        void getUsers_NoFilters_ReturnsOkWithPage() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(List.of(mockUserResponse));
            when(adminUserService.getUsersForAdmin(eq(null), eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("student@test.com"))
                    .andExpect(jsonPath("$.content[0].role").value("STUDENT"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(adminUserService, times(1))
                    .getUsersForAdmin(isNull(), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should forward role filter to service")
        void getUsers_WithRoleFilter_PassesRoleToService() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(List.of(mockUserResponse));
            when(adminUserService.getUsersForAdmin(eq(Role.STUDENT), eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users").param("role", "STUDENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(adminUserService).getUsersForAdmin(eq(Role.STUDENT), eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("Should forward isActive filter to service")
        void getUsers_WithIsActiveFilter_PassesIsActiveToService() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(List.of(mockUserResponse));
            when(adminUserService.getUsersForAdmin(eq(null), eq(true), eq(null), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users").param("isActive", "true"))
                    .andExpect(status().isOk());

            verify(adminUserService).getUsersForAdmin(isNull(), eq(true), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should forward search keyword to service")
        void getUsers_WithSearchParam_PassesSearchToService() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(List.of(mockUserResponse));
            when(adminUserService.getUsersForAdmin(eq(null), eq(null), eq("admin"), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users").param("search", "admin"))
                    .andExpect(status().isOk());

            verify(adminUserService).getUsersForAdmin(isNull(), isNull(), eq("admin"), any(Pageable.class));
        }

        @Test
        @DisplayName("Should forward all filters combined to service")
        void getUsers_AllFilters_PassesAllToService() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(List.of(mockUserResponse));
            when(adminUserService.getUsersForAdmin(eq(Role.STUDENT), eq(true), eq("student"), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users")
                            .param("role", "STUDENT")
                            .param("isActive", "true")
                            .param("search", "student"))
                    .andExpect(status().isOk());

            verify(adminUserService).getUsersForAdmin(eq(Role.STUDENT), eq(true), eq("student"), any(Pageable.class));
        }

        @Test
        @DisplayName("Should return empty page when no users match")
        void getUsers_NoUsersMatch_ReturnsEmptyPage() throws Exception {
            // Given
            Page<UserResponse> emptyPage = new PageImpl<>(Collections.emptyList());
            when(adminUserService.getUsersForAdmin(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // When & Then
            mockMvc.perform(get("/admin/users").param("role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("Should use default page size of 10")
        void getUsers_DefaultPageable_UsesSizeOf10() throws Exception {
            // Given
            Page<UserResponse> page = new PageImpl<>(Collections.emptyList());
            when(adminUserService.getUsersForAdmin(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(page);

            // When & Then
            mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isOk());

            // Verify default page size = 10 (page=0, size=10)
            verify(adminUserService).getUsersForAdmin(
                    eq(null), eq(null), eq(null),
                    eq(org.springframework.data.domain.PageRequest.of(0, 10))
            );
        }
    }

    // ==========================================
    // Tests for PATCH /admin/users/{id}/status
    // ==========================================

    @Nested
    @DisplayName("PATCH /admin/users/{id}/status")
    class UpdateStatus {

        @Test
        @DisplayName("Should deactivate user and return success message")
        void updateStatus_DeactivateUser_ReturnsOkWithMessage() throws Exception {
            // Given
            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(false);

            // When & Then
            mockMvc.perform(patch("/admin/users/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Trạng thái tài khoản đã được cập nhật thành công."));

            verify(adminUserService, times(1))
                    .updateUserStatus(eq(1L), any(UpdateUserStatusRequest.class), eq("admin@test.com"));
        }

        @Test
        @DisplayName("Should activate user and return success message")
        void updateStatus_ActivateUser_ReturnsOkWithMessage() throws Exception {
            // Given
            UpdateUserStatusRequest request = new UpdateUserStatusRequest();
            request.setIsActive(true);

            // When & Then
            mockMvc.perform(patch("/admin/users/2/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Trạng thái tài khoản đã được cập nhật thành công."));

            verify(adminUserService, times(1))
                    .updateUserStatus(eq(2L), any(UpdateUserStatusRequest.class), eq("admin@test.com"));
        }


        @Test
        @DisplayName("Should return 400 Bad Request when isActive is missing")
        void updateStatus_MissingIsActive_ReturnsBadRequest() throws Exception {
            // Given – request body thiếu field isActive
            String requestJson = "{}";

            // When & Then
            mockMvc.perform(patch("/admin/users/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }
}
