package com.codegym.mathclass.config.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class VaryHeaderFilterTest {

    private VaryHeaderFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new VaryHeaderFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Response có Content-Type là application/json -> Tự động thêm header Vary: Accept-Language")
    void doFilterInternal_whenJsonContentType_shouldAddVaryHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/json;charset=UTF-8");

        filter.doFilterInternal(request, response, (req, res) -> {
            // Simulate controller writing response
        });

        assertTrue(response.getHeaders("Vary").contains("Accept-Language"));
    }

    @Test
    @DisplayName("Response không phải application/json -> Không thêm header Vary")
    void doFilterInternal_whenNonJsonContentType_shouldNotAddVaryHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertFalse(response.getHeaders("Vary").contains("Accept-Language"));
    }

    @Test
    @DisplayName("Response đã có header Vary: Accept-Language -> Không bị trùng lặp header")
    void doFilterInternal_whenVaryHeaderAlreadyPresent_shouldNotDuplicate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/json");
        response.addHeader("Vary", "Accept-Language");

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertEquals(1, response.getHeaders("Vary").size());
    }

    @Test
    @DisplayName("Response đã có header Vary gộp (Origin, Accept-Language) -> Không thêm trùng")
    void doFilterInternal_whenVaryContainsComposite_shouldNotDuplicate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/json");
        response.addHeader("Vary", "Origin, Accept-Language");

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertEquals(1, response.getHeaders("Vary").size());
    }
}
