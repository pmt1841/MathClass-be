package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.ProviderCreateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import com.codegym.mathclass.aiconfig.service.ConnectionTestService;
import com.codegym.mathclass.aiconfig.service.ProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProviderControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProviderService providerService;

    @Mock
    private ConnectionTestService connectionTestService;

    @InjectMocks
    private ProviderController providerController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(providerController).build();
    }

    @Test
    @DisplayName("TC-CTRL-01: GET /api/v1/providers trả về danh sách Providers")
    void testGetAllProviders_Success() throws Exception {
        ProviderResponse response = ProviderResponse.builder()
                .id(1L)
                .code("GEMINI")
                .name("Google Gemini")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .strategy(ProviderStrategy.PRIORITY)
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .build();

        when(providerService.getAllProviders()).thenReturn(List.of(response));

        mockMvc.perform(get("/providers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("GEMINI"))
                .andExpect(jsonPath("$.data[0].name").value("Google Gemini"));
    }

    @Test
    @DisplayName("TC-CTRL-02: GET /api/v1/providers/{id}/models trả về danh sách models")
    void testGetProviderModels_Success() throws Exception {
        when(connectionTestService.fetchAvailableModels(1L)).thenReturn(List.of("gemini-1.5-flash", "gemini-1.5-pro"));

        mockMvc.perform(get("/providers/1/models")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("gemini-1.5-flash"))
                .andExpect(jsonPath("$.data[1]").value("gemini-1.5-pro"));
    }

    @Test
    @DisplayName("TC-CTRL-03: POST /api/v1/providers tạo mới Provider thành công")
    void testCreateProvider_Success() throws Exception {
        ProviderCreateRequest request = ProviderCreateRequest.builder()
                .code("OPENAI")
                .name("OpenAI")
                .baseUrl("https://api.openai.com/v1")
                .strategy(ProviderStrategy.ROUND_ROBIN)
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .build();

        ProviderResponse response = ProviderResponse.builder()
                .id(2L)
                .code("OPENAI")
                .name("OpenAI")
                .baseUrl("https://api.openai.com/v1")
                .strategy(ProviderStrategy.ROUND_ROBIN)
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .build();

        when(providerService.createProvider(any(ProviderCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OPENAI"))
                .andExpect(jsonPath("$.name").value("OpenAI"));
    }
}
