package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.ApiKeyCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyStatusPatchRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ApiKeyResponse;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import com.codegym.mathclass.aiconfig.service.ApiKeyService;
import com.codegym.mathclass.aiconfig.service.ConnectionTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ConnectionTestService connectionTestService;

    @InjectMocks
    private ApiKeyController apiKeyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiKeyController).build();
    }

    private ApiKeyResponse buildApiKeyResponse(Long id, String name, ApiKeyStatus status) {
        return ApiKeyResponse.builder()
                .id(id)
                .name(name)
                .maskedApiKey("sk-...1234")
                .priority(1)
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("GET /providers/{providerId}/keys Tests")
    class GetKeysByProviderIdTests {

        @Test
        @DisplayName("Should return list of api keys for provider")
        void getKeysByProviderId_Success() throws Exception {
            ApiKeyResponse response = buildApiKeyResponse(10L, "Gemini Key 1", ApiKeyStatus.ACTIVE);
            when(apiKeyService.getKeysByProviderId(1L)).thenReturn(List.of(response));

            mockMvc.perform(get("/providers/1/keys")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(10L))
                    .andExpect(jsonPath("$.data[0].name").value("Gemini Key 1"))
                    .andExpect(jsonPath("$.data[0].maskedApiKey").value("sk-...1234"))
                    .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("POST /providers/{providerId}/keys Tests")
    class AddKeyTests {

        @Test
        @DisplayName("Should add new api key and return 201 Created")
        void addKey_Success() throws Exception {
            ApiKeyCreateRequest request = new ApiKeyCreateRequest();
            request.setName("New Key");
            request.setApiKey("sk-secret-key-12345");
            request.setPriority(2);

            ApiKeyResponse response = buildApiKeyResponse(20L, "New Key", ApiKeyStatus.ACTIVE);
            when(apiKeyService.addKey(eq(1L), any(ApiKeyCreateRequest.class))).thenReturn(response);

            mockMvc.perform(post("/providers/1/keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(20L))
                    .andExpect(jsonPath("$.name").value("New Key"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("DELETE /keys/{keyId} Tests")
    class DeleteKeyTests {

        @Test
        @DisplayName("Should delete api key successfully and return 204 No Content")
        void deleteKey_Success() throws Exception {
            doNothing().when(apiKeyService).deleteKey(10L);

            mockMvc.perform(delete("/keys/10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(apiKeyService).deleteKey(10L);
        }
    }

    @Nested
    @DisplayName("PATCH /keys/{keyId} Tests")
    class UpdateKeyStatusTests {

        @Test
        @DisplayName("Should update key status and return 200 OK")
        void updateKeyStatus_Success() throws Exception {
            ApiKeyStatusPatchRequest request = new ApiKeyStatusPatchRequest();
            request.setStatus(ApiKeyStatus.INACTIVE);

            ApiKeyResponse response = buildApiKeyResponse(10L, "Gemini Key 1", ApiKeyStatus.INACTIVE);
            when(apiKeyService.updateKeyStatus(eq(10L), any(ApiKeyStatusPatchRequest.class))).thenReturn(response);

            mockMvc.perform(patch("/keys/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }
    }

    @Nested
    @DisplayName("PUT /keys/{keyId} Tests")
    class UpdateKeyTests {

        @Test
        @DisplayName("Should update key details and return 200 OK")
        void updateKey_Success() throws Exception {
            ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                    .name("Updated Name")
                    .priority(5)
                    .status(ApiKeyStatus.ACTIVE)
                    .build();

            ApiKeyResponse response = buildApiKeyResponse(10L, "Updated Name", ApiKeyStatus.ACTIVE);
            when(apiKeyService.updateKey(eq(10L), any(ApiKeyUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(put("/keys/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.name").value("Updated Name"));
        }
    }

    @Nested
    @DisplayName("POST /keys/{keyId}/verify Tests")
    class VerifyKeyTests {

        @Test
        @DisplayName("Should verify key connectivity and return 200 OK")
        void verifyKey_Success() throws Exception {
            TestConnectionResponse response = TestConnectionResponse.builder()
                    .success(true)
                    .message("Key hợp lệ và hoạt động tốt")
                    .latencyMs(150L)
                    .build();

            when(connectionTestService.verifyKey(10L)).thenReturn(response);

            mockMvc.perform(post("/keys/10/verify")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.latencyMs").value(150))
                    .andExpect(jsonPath("$.message").value("Key hợp lệ và hoạt động tốt"));
        }
    }
}
