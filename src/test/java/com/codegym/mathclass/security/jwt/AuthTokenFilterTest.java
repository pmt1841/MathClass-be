package com.codegym.mathclass.security.jwt;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.security.services.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private AuthTokenFilter authTokenFilter;

    @BeforeEach
    void setUp() {
        authTokenFilter = new AuthTokenFilter(jwtUtils, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal thiết lập Authentication khi Header Authorization chứa JWT hợp lệ")
    void testDoFilterInternalWithValidBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer valid.jwt.token");

        CustomUserDetails userDetails = new CustomUserDetails(
                1L, "Nguyen Van A", "test@example.com", "password", true, null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        when(jwtUtils.getJwtFromCookies(request)).thenReturn(null);
        when(jwtUtils.validateJwtToken("valid.jwt.token")).thenReturn(true);
        when(jwtUtils.getScopeFromJwtToken("valid.jwt.token")).thenReturn(null);
        when(jwtUtils.getUserNameFromJwtToken("valid.jwt.token")).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);

        authTokenFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@example.com", ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getEmail());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal thiết lập Authentication khi Cookie chứa JWT hợp lệ")
    void testDoFilterInternalWithValidCookieToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        CustomUserDetails userDetails = new CustomUserDetails(
                2L, "Tran Thi B", "student@example.com", "password", true, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        when(jwtUtils.getJwtFromCookies(request)).thenReturn("cookie.jwt.token");
        when(jwtUtils.validateJwtToken("cookie.jwt.token")).thenReturn(true);
        when(jwtUtils.getScopeFromJwtToken("cookie.jwt.token")).thenReturn(null);
        when(jwtUtils.getUserNameFromJwtToken("cookie.jwt.token")).thenReturn("student@example.com");
        when(userDetailsService.loadUserByUsername("student@example.com")).thenReturn(userDetails);

        authTokenFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(2L, ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal bỏ qua authentication khi không có token")
    void testDoFilterInternalWithoutToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtils.getJwtFromCookies(request)).thenReturn(null);

        authTokenFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("doFilterInternal không set authentication khi token không hợp lệ")
    void testDoFilterInternalWithInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid.jwt.token");

        when(jwtUtils.getJwtFromCookies(request)).thenReturn(null);
        when(jwtUtils.validateJwtToken("invalid.jwt.token")).thenReturn(false);

        authTokenFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("doFilterInternal dừng và chuyển tiếp ngay nếu token có scope PRE_AUTH")
    void testDoFilterInternalWithPreAuthToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer preauth.jwt.token");

        when(jwtUtils.getJwtFromCookies(request)).thenReturn(null);
        when(jwtUtils.validateJwtToken("preauth.jwt.token")).thenReturn(true);
        when(jwtUtils.getScopeFromJwtToken("preauth.jwt.token")).thenReturn(JwtUtils.PRE_AUTH_SCOPE);

        authTokenFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("doFilterInternal trả về 403 và xóa cookie nếu tài khoản bị khóa")
    void testDoFilterInternalWhenAccountIsLocked() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer locked.jwt.token");

        CustomUserDetails lockedUser = new CustomUserDetails(
                3L, "Locked User", "locked@example.com", "password", false, null, "Vi phạm quy chế", null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        when(jwtUtils.getJwtFromCookies(request)).thenReturn(null);
        when(jwtUtils.validateJwtToken("locked.jwt.token")).thenReturn(true);
        when(jwtUtils.getScopeFromJwtToken("locked.jwt.token")).thenReturn(null);
        when(jwtUtils.getUserNameFromJwtToken("locked.jwt.token")).thenReturn("locked@example.com");
        when(userDetailsService.loadUserByUsername("locked@example.com")).thenReturn(lockedUser);
        when(jwtUtils.getCleanJwtCookie()).thenReturn(ResponseCookie.from("mathclass_jwt", "").build());
        when(jwtUtils.getCleanJwtRefreshCookie()).thenReturn(ResponseCookie.from("mathclass_refresh", "").build());

        authTokenFilter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_LOCKED"));
        assertTrue(response.getContentAsString().contains("Vi phạm quy chế"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }
}
