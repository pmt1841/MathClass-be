package com.codegym.mathclass.auth.controller;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass spring security filters for unit test
class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AuthService authService;

        private ObjectMapper objectMapper = new ObjectMapper();

        private LoginRequest loginRequest;
        private SignupRequest signupRequest;

        @BeforeEach
        void setUp() {
                loginRequest = new LoginRequest();
                loginRequest.setEmail("test@gmail.com");
                loginRequest.setPassword("123456");

                signupRequest = new SignupRequest();
                signupRequest.setEmail("newuser@gmail.com");
                signupRequest.setPassword("123456");
                signupRequest.setFullName("New User");
                signupRequest.setRole(Role.STUDENT);
                signupRequest.setPhoneNumber("0123456789");
        }

        @Test
        @DisplayName("Should login successfully")
        void login_Success() throws Exception {
                when(authService.authenticateUser(any(LoginRequest.class)))
                                .thenReturn((ResponseEntity) ResponseEntity.ok(new MessageResponse("Login success")));

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Login success"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request if login request is invalid (missing email)")
        void login_InvalidRequest() throws Exception {
                loginRequest.setEmail(null); // Invalid: email cannot be null

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should register successfully")
        void register_Success() throws Exception {
                when(authService.registerUser(any(SignupRequest.class)))
                                .thenReturn((ResponseEntity) ResponseEntity
                                                .ok(new MessageResponse("Register success")));

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Register success"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request if signup request is invalid (missing full name)")
        void register_InvalidRequest() throws Exception {
                signupRequest.setFullName(null); // Invalid: full name may be required depending on validation

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should logout successfully")
        void logout_Success() throws Exception {
                when(authService.logoutUser())
                                .thenReturn((ResponseEntity) ResponseEntity.ok(new MessageResponse("Logout success")));

                mockMvc.perform(post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Logout success"));
        }

        @Test
        @DisplayName("Should verify user successfully")
        void verifyUser_Success() throws Exception {
                String token = "dummy-token";
                when(authService.verifyUser(token))
                                .thenReturn((ResponseEntity) ResponseEntity.ok(new MessageResponse("Verify success")));

                mockMvc.perform(get("/api/auth/verify")
                                .param("token", token)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Verify success"));
        }
}
