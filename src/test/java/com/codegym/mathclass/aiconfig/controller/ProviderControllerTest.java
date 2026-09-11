package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.ProviderCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ProviderUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.request.TestConnectionRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("TC-CTRL-04: GET /api/v1/providers/{id} trả về chi tiết Provider")
    void testGetProviderById_Success() throws Exception {
        ProviderResponse response = ProviderResponse.builder()
                .id(1L)
                .code("GEMINI")
                .name("Google Gemini")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .strategy(ProviderStrategy.PRIORITY)
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .build();

        when(providerService.getProviderById(1L)).thenReturn(response);

        mockMvc.perform(get("/providers/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.code").value("GEMINI"))
                .andExpect(jsonPath("$.name").value("Google Gemini"));
    }

    @Test
    @DisplayName("TC-CTRL-05: PUT /api/v1/providers/{id} cập nhật Provider thành công")
    void testUpdateProvider_Success() throws Exception {
        ProviderUpdateRequest request = ProviderUpdateRequest.builder()
                .name("Updated Gemini")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .strategy(ProviderStrategy.ROUND_ROBIN)
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .build();

        ProviderResponse response = ProviderResponse.builder()
                .id(1L)
                .code("GEMINI")
                .name("Updated Gemini")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .strategy(ProviderStrategy.ROUND_ROBIN)
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .build();

        when(providerService.updateProvider(eq(1L), any(ProviderUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/providers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Gemini"))
                .andExpect(jsonPath("$.strategy").value("ROUND_ROBIN"));
    }

    @Test
    @DisplayName("TC-CTRL-06: DELETE /api/v1/providers/{id} xóa Provider thành công")
    void testDeleteProvider_Success() throws Exception {
        doNothing().when(providerService).deleteProvider(1L);

        mockMvc.perform(delete("/providers/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(providerService).deleteProvider(1L);
    }

    @Test
    @DisplayName("TC-CTRL-07: POST /api/v1/providers/test kiểm tra kết nối Provider thành công")
    void testTestConnection_Success() throws Exception {
        TestConnectionRequest request = TestConnectionRequest.builder()
                .providerCode("GEMINI")
                .apiKey("test-key")
                .model("gemini-1.5-flash")
                .build();

        TestConnectionResponse response = TestConnectionResponse.builder()
                .success(true)
                .message("Kết nối thành công")
                .latencyMs(120L)
                .build();

        when(connectionTestService.testConnection(any(TestConnectionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/providers/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.latencyMs").value(120));
    }
}
