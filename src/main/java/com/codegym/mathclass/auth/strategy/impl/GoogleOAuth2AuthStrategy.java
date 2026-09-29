package com.codegym.mathclass.auth.strategy.impl;

import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.UserRegistrationService;
import com.codegym.mathclass.auth.strategy.AuthStrategy;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class GoogleOAuth2AuthStrategy implements AuthStrategy<GoogleAuthRequest> {

    private final UserRepository userRepository;
    private final PermissionCacheService permissionCacheService;
    private final UserRegistrationService userRegistrationService;
    private final AuthSessionService authSessionService;
    private final RestTemplate restTemplate;

    public GoogleOAuth2AuthStrategy(
            UserRepository userRepository,
            PermissionCacheService permissionCacheService,
            UserRegistrationService userRegistrationService,
            AuthSessionService authSessionService,
            @Qualifier("authRestTemplate") RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.permissionCacheService = permissionCacheService;
        this.userRegistrationService = userRegistrationService;
        this.authSessionService = authSessionService;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(AuthType authType) {
        return authType == AuthType.GOOGLE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserInfoResponse authenticate(GoogleAuthRequest request, HttpServletResponse httpResponse) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(request.getCredential());
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = response.getBody();

                String email = (String) payload.get("email");
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                Optional<User> userOptional = userRepository.findByEmail(email);
                User user;

                if (userOptional.isPresent()) {
                    user = userOptional.get();

                    if (!user.isActive()) {
                        throw new BadRequestException(
                                "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
                    }

                    if (request.getRole() != null && !request.getRole().isEmpty()) {
                        try {
                            Role requestedRole = Role.valueOf(request.getRole().toUpperCase());
                            if (requestedRole == Role.TEACHER && user.getRole() == Role.STUDENT) {
                                throw new BadRequestException(
                                        "Tài khoản học sinh không thể truy cập hệ thống của giáo viên.");
                            }
                            if (requestedRole == Role.STUDENT && user.getRole() != Role.STUDENT) {
                                throw new BadRequestException(
                                        "Tài khoản giáo viên không thể truy cập hệ thống của học sinh.");
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                        user.setAvatarUrl(pictureUrl);
                        userRepository.save(user);
                    }
                } else {
                    Role role = Role.STUDENT;
                    if (request.getRole() != null) {
                        try {
                            role = Role.valueOf(request.getRole().toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    user = userRegistrationService.registerOAuth2User(email, name, pictureUrl, role);
                }

                List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
                CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                return authSessionService.issueAuthSession(user, request.isRememberMe(), httpResponse);

            } else {
                throw new BadRequestException("Token xác thực Google không hợp lệ.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi xác thực qua Google SSO: {}", e.getMessage(), e);
            throw new BadRequestException("Đăng nhập Google thất bại. Vui lòng thử lại sau.");
        }
    }
}
