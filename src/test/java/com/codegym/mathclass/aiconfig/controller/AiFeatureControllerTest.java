package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiFeatureControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @InjectMocks
    private AiFeatureController aiFeatureController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiFeatureController).build();
    }

    private TaskConfig taskConfig(boolean enabled, ProviderStatus providerStatus) {
        Provider provider = new Provider();
        provider.setId(1L);
        provider.setStatus(providerStatus);
        TaskConfig config = new TaskConfig();
        config.setTask("SUBMISSION_GRADING");
        config.setEnabled(enabled);
        config.setProvider(provider);
        return config;
    }

    @Nested
    @DisplayName("GET /ai/features Tests")
    class GetFeaturesEndpointTests {

        @Test
        @DisplayName("Should return true for configured + enabled task with ACTIVE provider")
        void getFeatures_enabledTask_returnsTrue() throws Exception {
            when(taskConfigRepository.findByTask("SUBMISSION_GRADING"))
                    .thenReturn(Optional.of(taskConfig(true, ProviderStatus.ACTIVE)));

            mockMvc.perform(get("/ai/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.SUBMISSION_GRADING").value(true));
        }

        @Test
        @DisplayName("Should return false for tasks not configured in database")
        void getFeatures_notConfigured_returnsFalse() throws Exception {
            when(taskConfigRepository.findByTask("SUBMISSION_GRADING")).thenReturn(Optional.empty());

            mockMvc.perform(get("/ai/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.SUBMISSION_GRADING").value(false))
                    .andExpect(jsonPath("$.STUDENT_HINT").value(false));
        }

        @Test
        @DisplayName("Should return false when task config is disabled (enabled=false)")
        void getFeatures_taskDisabled_returnsFalse() throws Exception {
            when(taskConfigRepository.findByTask("SUBMISSION_GRADING"))
                    .thenReturn(Optional.of(taskConfig(false, ProviderStatus.ACTIVE)));

            mockMvc.perform(get("/ai/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.SUBMISSION_GRADING").value(false));
        }

        @Test
        @DisplayName("Should return false when linked provider is INACTIVE")
        void getFeatures_providerInactive_returnsFalse() throws Exception {
            when(taskConfigRepository.findByTask("SUBMISSION_GRADING"))
                    .thenReturn(Optional.of(taskConfig(true, ProviderStatus.INACTIVE)));

            mockMvc.perform(get("/ai/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.SUBMISSION_GRADING").value(false));
        }

        @Test
        @DisplayName("Should return all known feature task keys")
        void getFeatures_returnsAllKnownTasks() throws Exception {
            when(taskConfigRepository.findByTask("SUBMISSION_GRADING"))
                    .thenReturn(Optional.of(taskConfig(true, ProviderStatus.ACTIVE)));

            mockMvc.perform(get("/ai/features"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.SUBMISSION_GRADING").value(true))
                    .andExpect(jsonPath("$.STUDENT_HINT").value(false))
                    .andExpect(jsonPath("$.QUESTION_GEN").value(false))
                    .andExpect(jsonPath("$.CANVAS_LATEX").value(false))
                    .andExpect(jsonPath("$.ERROR_ANALYSIS").value(false));
        }
    }
}
