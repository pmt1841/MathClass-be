package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageCreateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.CreditPackageUpdateRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditPackageResponse;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCreditPackageControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiCreditService aiCreditService;

    @InjectMocks
    private AdminCreditPackageController adminCreditPackageController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCreditPackageController).build();
    }

    private CreditPackageResponse buildPackageResponse(Long id, String name, Integer credits, Integer price) {
        return CreditPackageResponse.builder()
                .id(id)
                .name(name)
                .credits(credits)
                .price(price)
                .enabled(true)
                .sortOrder(1)
                .build();
    }

    @Nested
    @DisplayName("GET /admin/credit-packages Tests")
    class ListPackagesTests {

        @Test
        @DisplayName("Should return list of all credit packages")
        void listPackages_Success() throws Exception {
            CreditPackageResponse pkg = buildPackageResponse(1L, "Gói Cơ Bản", 100, 50000);
            when(aiCreditService.getAllPackages()).thenReturn(List.of(pkg));

            mockMvc.perform(get("/admin/credit-packages")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1L))
                    .andExpect(jsonPath("$[0].name").value("Gói Cơ Bản"))
                    .andExpect(jsonPath("$[0].credits").value(100))
                    .andExpect(jsonPath("$[0].price").value(50000));
        }
    }

    @Nested
    @DisplayName("POST /admin/credit-packages Tests")
    class CreatePackageTests {

        @Test
        @DisplayName("Should create credit package and return 200 OK")
        void createPackage_Success() throws Exception {
            CreditPackageCreateRequest request = CreditPackageCreateRequest.builder()
                    .name("Gói Nâng Cao")
                    .credits(300)
                    .price(120000)
                    .enabled(true)
                    .sortOrder(2)
                    .build();

            CreditPackageResponse response = buildPackageResponse(2L, "Gói Nâng Cao", 300, 120000);
            when(aiCreditService.createPackage(any(CreditPackageCreateRequest.class))).thenReturn(response);

            mockMvc.perform(post("/admin/credit-packages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2L))
                    .andExpect(jsonPath("$.name").value("Gói Nâng Cao"))
                    .andExpect(jsonPath("$.credits").value(300));
        }
    }

    @Nested
    @DisplayName("PUT /admin/credit-packages/{id} Tests")
    class UpdatePackageTests {

        @Test
        @DisplayName("Should update credit package and return 200 OK")
        void updatePackage_Success() throws Exception {
            CreditPackageUpdateRequest request = CreditPackageUpdateRequest.builder()
                    .name("Gói Nâng Cao Updated")
                    .credits(350)
                    .price(130000)
                    .enabled(true)
                    .sortOrder(2)
                    .build();

            CreditPackageResponse response = buildPackageResponse(2L, "Gói Nâng Cao Updated", 350, 130000);
            when(aiCreditService.updatePackage(eq(2L), any(CreditPackageUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(put("/admin/credit-packages/2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2L))
                    .andExpect(jsonPath("$.name").value("Gói Nâng Cao Updated"))
                    .andExpect(jsonPath("$.credits").value(350));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/credit-packages/{id} Tests")
    class DeletePackageTests {

        @Test
        @DisplayName("Should delete credit package and return 200 OK")
        void deletePackage_Success() throws Exception {
            doNothing().when(aiCreditService).deletePackage(2L);

            mockMvc.perform(delete("/admin/credit-packages/2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Xóa gói credit thành công"));

            verify(aiCreditService).deletePackage(2L);
        }
    }
}
