package com.codegym.mathclass.security.jwt;

import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String TEST_SECRET = "dGhpc19pc19hX3NlY3VyZV9hbmRfZ2VuZXJhdGVkX2Jhc2U2NF9rZXlfZm9yX21hdGhjY2xhc3NfYXBwbGljYXRpb25fNTEyYml0cwo=";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 300000);
        ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "mathclass_jwt");
        ReflectionTestUtils.setField(jwtUtils, "jwtRefreshCookie", "mathclass_jwt_refresh");
    }

    private String decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        byte[] bytes = Base64.getUrlDecoder().decode(parts[1]);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private CustomUserDetails teacherUserDetails() {
        return new CustomUserDetails(
                1L,
                "Giáo viên Toán",
                "teacher@test.com",
                "password",
                true,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    @Test
    @DisplayName("generateJwtToken(Authentication) includes role claim in JWT payload")
    void generatesTokenWithRoleClaim() {
        CustomUserDetails userDetails = teacherUserDetails();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String jwt = jwtUtils.generateJwtToken(authentication);
        String payload = decodePayload(jwt);

        assertThat(payload).contains("\"role\":\"TEACHER\"");
        assertThat(payload).contains("teacher@test.com"); // subject
    }

    @Test
    @DisplayName("generateJwtToken(String) keeps backward compatibility (no role claim)")
    void generatesTokenWithoutRoleClaimWhenRoleUnknown() {
        String jwt = jwtUtils.generateJwtToken("student@test.com");
        String payload = decodePayload(jwt);

        assertThat(payload).contains("\"sub\":\"student@test.com\"");
        assertThat(payload).doesNotContain("\"role\"");
    }

    @Test
    @DisplayName("generateJwtCookie(CustomUserDetails, rememberMe) encodes role claim")
    void generatesCookieTokenWithRoleClaim() {
        CustomUserDetails userDetails = teacherUserDetails();

        var cookie = jwtUtils.generateJwtCookie(userDetails, true);

        assertThat(cookie.toString()).startsWith("mathclass_jwt=");
        String cookieValue = cookie.toString().split(";")[0].split("=", 2)[1];
        String jwt = URLDecoder.decode(cookieValue, StandardCharsets.UTF_8);
        assertThat(decodePayload(jwt)).contains("\"role\":\"TEACHER\"");
    }
}
