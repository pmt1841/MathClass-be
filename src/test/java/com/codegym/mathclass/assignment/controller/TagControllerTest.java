package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.response.TagResponse;
import com.codegym.mathclass.assignment.entity.TagType;
import com.codegym.mathclass.assignment.service.TagService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TagService tagService;

    @InjectMocks
    private TagController tagController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tagController).build();
    }

    @Test
    @DisplayName("GET /tags with query - Should search tags")
    void getTags_WithQuery_ReturnsSearchedTags() throws Exception {
        TagResponse tag = TagResponse.builder()
                .id(1L)
                .name("Hình học")
                .type(TagType.SUBJECT)
                .build();

        when(tagService.searchTags("Hình")).thenReturn(List.of(tag));

        mockMvc.perform(get("/tags")
                        .param("query", "Hình")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Hình học"))
                .andExpect(jsonPath("$[0].type").value("SUBJECT"));
    }

    @Test
    @DisplayName("GET /tags without query - Should get active tags by type")
    void getTags_WithoutQuery_ReturnsActiveTagsByType() throws Exception {
        TagResponse tag = TagResponse.builder()
                .id(2L)
                .name("Lớp 10")
                .type(TagType.GRADE)
                .build();

        when(tagService.getActiveTags(TagType.GRADE)).thenReturn(List.of(tag));

        mockMvc.perform(get("/tags")
                        .param("type", "GRADE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].name").value("Lớp 10"))
                .andExpect(jsonPath("$[0].type").value("GRADE"));
    }
}
