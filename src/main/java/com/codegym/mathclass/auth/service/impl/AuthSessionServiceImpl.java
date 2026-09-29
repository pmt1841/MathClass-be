package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.entity.RefreshToken;
import com.codegym.mathclass.auth.service.AuthSessionService;
import com.codegym.mathclass.auth.service.RefreshTokenService;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.PermissionCacheService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final PermissionCacheService permissionCacheService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserInfoResponse issueAuthSession(User user, boolean rememberMe, HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now();
        user.setLastActiveAt(now);
        userRepository.save(user);

        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
        CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails, rememberMe);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(refreshToken.getToken(), rememberMe);

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString());

        ResponseCookie cleanLoggedOutCookie = ResponseCookie.from("mathclass_logged_out", "")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cleanLoggedOutCookie.toString());

        String jwtToken = jwtUtils.generateJwtToken(userDetails.getUsername(), user.getRole().name());
        return userMapper.toUserInfoResponse(userDetails, jwtToken);
    }

    @Override
    @Transactional
    public UserInfoResponse issueAuthSession(CustomUserDetails userDetails, boolean rememberMe, HttpServletResponse response) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng với ID: " + userDetails.getId()));
        return issueAuthSession(user, rememberMe, response);
    }

    @Override
    public void setRefreshAccessCookie(User user, HttpServletResponse response) {
        List<String> permissions = permissionCacheService.getPermissionsByRole(user.getRole());
        CustomUserDetails userDetails = CustomUserDetails.build(user, permissions);
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
    }

    @Override
    public void clearSessionCookies(HttpServletResponse response) {
        ResponseCookie cleanJwtCookie = jwtUtils.getCleanJwtCookie();
        ResponseCookie cleanJwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();

        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cleanJwtRefreshCookie.toString());
    }
}
