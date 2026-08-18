package com.codegym.mathclass.security.jwt;

import java.util.Date;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.codegym.mathclass.security.services.CustomUserDetails;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtUtils {

    @Value("${mathclass.app.jwtSecret:dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhjY2xhc3NfYXBwbGljYXRpb25fNTEyYml0cwo=}")
    private String jwtSecret;

    @Value("${mathclass.app.jwtExpirationMs:300000}")
    private int jwtExpirationMs;

    @Value("${mathclass.app.jwtCookieName:mathclass_jwt}")
    private String jwtCookie;

    @Value("${mathclass.app.jwtRefreshCookieName:mathclass_jwt_refresh}")
    private String jwtRefreshCookie;

    @Value("${mathclass.app.apiPrefix:/api/v1}")
    private String apiPrefix;

    public String getJwtFromCookies(HttpServletRequest request) {
        return getCookieValueByName(request, jwtCookie);
    }

    public String getJwtRefreshFromCookies(HttpServletRequest request) {
        return getCookieValueByName(request, jwtRefreshCookie);
    }

    private String getCookieValueByName(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    public ResponseCookie generateJwtCookie(CustomUserDetails userPrincipal, boolean rememberMe) {
        String jwt = generateJwtToken(userPrincipal.getUsername(), extractRole(userPrincipal));
        Long maxAge = rememberMe ? jwtExpirationMs / 1000L : -1L;
        return generateCookie(jwtCookie, jwt, "/", maxAge);
    }

    public ResponseCookie generateJwtCookie(CustomUserDetails userPrincipal) {
        return generateJwtCookie(userPrincipal, true);
    }

    public ResponseCookie generateJwtCookie(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        return generateJwtCookie(userPrincipal, true);
    }

    public String generateJwtToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        return generateJwtToken(userPrincipal.getUsername(), extractRole(userPrincipal));
    }

    /**
     * Ghi claim {@code role} (ADMIN/TEACHER/STUDENT) vào JWT để proxy/middleware phía frontend
     * có thể xác thực vai trò dựa trên token đã ký, thay vì cookie {@code mathclass_role}
     * do client tự đặt (dễ bị giả mạo).
     * Tương thích ngược: token cũ không có claim role vẫn hợp lệ — frontend sẽ fallback sang cookie.
     */
    public String generateJwtToken(String username, String role) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs));
        if (role != null && !role.isEmpty()) {
            builder.claim("role", role);
        }
        return builder.signWith(key()).compact();
    }

    public String generateJwtToken(String username) {
        return generateJwtToken(username, null);
    }

    private String extractRole(CustomUserDetails principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.substring("ROLE_".length()))
                .orElse(null);
    }

    public ResponseCookie generateRefreshJwtCookie(String refreshToken, boolean rememberMe) {
        Long maxAge = rememberMe ? 7 * 24 * 60 * 60L : -1L;
        return generateCookie(jwtRefreshCookie, refreshToken, "/", maxAge);
    }

    public ResponseCookie generateRefreshJwtCookie(String refreshToken) {
        return generateRefreshJwtCookie(refreshToken, true);
    }

    public ResponseCookie getCleanJwtCookie() {
        return ResponseCookie.from(jwtCookie, "").path("/").maxAge(0).build();
    }

    public ResponseCookie getCleanJwtRefreshCookie() {
        return ResponseCookie.from(jwtRefreshCookie, "").path("/").maxAge(0).build();
    }

    private ResponseCookie generateCookie(String name, String value, String path, Long maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .path(path)
                .httpOnly(true)
                .secure(false) // Có thể đổi thành true nếu dùng HTTPS
                .sameSite("Lax");

        if (maxAge != null && maxAge >= 0) {
            builder.maxAge(maxAge);
        }

        return builder.build();
    }

    private javax.crypto.SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public static final String PRE_AUTH_SCOPE = "PRE_AUTH";
    private static final long PRE_AUTH_EXPIRATION_MS = 5 * 60 * 1000L; // 5 phút

    public String generatePreAuthToken(String username, Long userId, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("scope", PRE_AUTH_SCOPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PRE_AUTH_EXPIRATION_MS))
                .signWith(key())
                .compact();
    }

    public boolean validatePreAuthToken(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return PRE_AUTH_SCOPE.equals(claims.get("scope", String.class));
        } catch (Exception e) {
            log.error("Invalid or expired Pre-Auth token: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserIdFromPreAuthToken(String token) {
        Object userIdObj = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String getScopeFromJwtToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("scope", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }

        return false;
    }
}
