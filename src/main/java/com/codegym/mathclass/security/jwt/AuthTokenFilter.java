package com.codegym.mathclass.security.jwt;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.security.services.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthTokenFilter extends OncePerRequestFilter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    public AuthTokenFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String scope = jwtUtils.getScopeFromJwtToken(jwt);
                if (JwtUtils.PRE_AUTH_SCOPE.equals(scope)) {
                    // Pre-auth token is only valid for 2FA endpoints and should never authenticate general requests
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                /*
                 * BẢO MẬT & XÓA COOKIE TỨC THÌ KHI BỊ KHÓA:
                 * Ngay khi phát hiện tài khoản đã bị khóa (!isEnabled() hoặc !isAccountNonLocked()),
                 * ngoài việc từ chối với mã HTTP 403 Forbidden (ACCOUNT_LOCKED),
                 * Backend sẽ tự động đính kèm các Set-Cookie header với Max-Age=0 để ép trình duyệt XÓA SẠCH
                 * các HttpOnly Cookie (mathclass_jwt, mathclass_refresh). Điều này ngăn ngừa hoàn toàn lỗi
                 * Middleware Next.js đọc nhầm cookie cũ và redirect bậy về /home gây đơ trắng màn hình.
                 */
                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    log.warn("Tài khoản [{}] đã bị khóa, lập tức ngắt phiên truy cập API và hủy cookie.", username);

                    ResponseCookie cleanJwtCookie = jwtUtils.getCleanJwtCookie();
                    ResponseCookie cleanRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
                    response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtCookie.toString());
                    response.addHeader(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString());

                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");

                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("code", "ACCOUNT_LOCKED");
                    errorBody.put("message", "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
                    
                    if (userDetails instanceof CustomUserDetails customUser) {
                        if (customUser.getLockReason() != null) {
                            errorBody.put("lockReason", customUser.getLockReason());
                        }
                        if (customUser.getLockedAt() != null) {
                            errorBody.put("lockedAt", customUser.getLockedAt().toString());
                        }
                    }

                    OBJECT_MAPPER.writeValue(response.getOutputStream(), errorBody);
                    return;
                }


                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromCookies(request);
        if (jwt != null && !jwt.isEmpty()) {
            return jwt;
        }
        
        // Fallback for old clients or mobile apps
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
