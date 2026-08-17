package com.codegym.mathclass.bugreport.controller;

import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.service.BugReportService;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BugReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BugReportService bugReportService;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @InjectMocks
    private BugReportController bugReportController;

    private ObjectMapper objectMapper;
    private BugReportResponse responseDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        SimpleModule pageModule = new SimpleModule();
        pageModule.addSerializer(PageImpl.class, new com.fasterxml.jackson.databind.ser.std.StdSerializer<PageImpl>(PageImpl.class) {
            @Override
            public void serialize(PageImpl page, com.fasterxml.jackson.core.JsonGenerator gen,
                                  com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
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

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(bugReportController)
                .setMessageConverters(converter)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        responseDto = BugReportResponse.builder()
                .id(1L)
                .reporterEmail("guest@mathclass.com")
                .errorType(BugErrorType.LOGIN_ACCOUNT)
                .status(BugReportStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Should send public report OTP successfully")
    void sendPublicReportOtp_Success() throws Exception {
        com.codegym.mathclass.bugreport.dto.SendOtpRequest request = new com.codegym.mathclass.bugreport.dto.SendOtpRequest("guest@gmail.com");

        mockMvc.perform(post("/bug-reports/public/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should upload public image successfully without ApiResponse wrapping")
    void uploadPublicBugReportImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image bytes".getBytes());
        when(supabaseStorageService.uploadImage(any(), eq("assignment_image"))).thenReturn("http://supabase.url/test.jpg");

        mockMvc.perform(multipart("/bug-reports/public/upload-image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("http://supabase.url/test.jpg"));
    }

    @Test
    @DisplayName("Should create public report directly returning DTO")
    void createPublicReport_Success() throws Exception {
        CreateBugReportRequest request = CreateBugReportRequest.builder()
                .reporterEmail("guest@mathclass.com")
                .errorType(BugErrorType.LOGIN_ACCOUNT)
                .description("Login bug")
                .build();

        when(bugReportService.createPublicReport(any(), any())).thenReturn(responseDto);

        mockMvc.perform(post("/bug-reports/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reporterEmail").value("guest@mathclass.com"));
    }

    @Test
    @DisplayName("Should get admin bug reports page directly returning Page DTO")
    void getReports_Success() throws Exception {
        Page<BugReportResponse> page = new PageImpl<>(Collections.singletonList(responseDto));
        when(bugReportService.getReports(any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/admin/bug-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @DisplayName("Should update report status returning updated DTO")
    void updateReportStatus_Success() throws Exception {
        UpdateBugReportStatusRequest request = new UpdateBugReportStatusRequest();
        request.setStatus(BugReportStatus.RESOLVED);
        responseDto.setStatus(BugReportStatus.RESOLVED);

        when(bugReportService.updateReportStatus(eq(1L), any())).thenReturn(responseDto);

        mockMvc.perform(patch("/admin/bug-reports/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
