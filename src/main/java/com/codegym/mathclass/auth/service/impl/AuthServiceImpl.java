package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.auth.service.AuthService;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.JwtResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.springframework.http.*;
import org.springframework.web.client.*;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder encoder;
    private final EmailService emailService;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                throw new BadRequestException(
                        "Bạn chưa thiết lập mật khẩu. Vui lòng bấm vào Quên mật khẩu để tạo mật khẩu mới, hoặc tiếp tục Đăng nhập bằng Google.");
            }
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getFullName(),
                userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .findFirst()
                        .map(r -> r.replace("ROLE_", ""))
                        .orElse(""),
                userDetails.getAvatarUrl());
    }

    @Override
    public MessageResponse logoutUser() {

        // Đối với JWT (stateless), việc logout thực chất do client thực hiện bằng cách
        // xoá token ở LocalStorage/Cookie
        // Backend chỉ cần trả về thông báo thành công
        SecurityContextHolder.getContext().setAuthentication(null);
        return new MessageResponse("Đăng xuất thành công!");
    }

    @Override
    public MessageResponse registerUser(SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new BadRequestException("Lỗi: Email đã tồn tại!");
        }

        // Tạo user mới
        User user = new User();
        user.setPassword(encoder.encode(signUpRequest.getPassword()));
        user.setFullName(signUpRequest.getFullName());
        user.setPhoneNumber(signUpRequest.getPhoneNumber());
        user.setEmail(signUpRequest.getEmail());
        user.setRole(signUpRequest.getRole());
        user.setActive(false);
        String token = UUID.randomUUID().toString();
        user.setVerificationCode(token);

        userRepository.save(user);

        // Khởi tạo cài đặt thông báo mặc định cho người dùng mới
        NotificationSettings settings = NotificationSettings.builder()
                .userId(user.getId())
                .build();
        notificationSettingsRepository.save(settings);

        String verifyLink = frontendUrl + "/verify?token=" + token;
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("verifyLink", verifyLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Xác nhận đăng ký tài khoản MathClass", "auth-verify", context);

        return new MessageResponse("Đăng ký tài khoản thành công! Vui lòng kiểm tra email để xác nhận.");
    }

    @Override
    public MessageResponse verifyUser(String token) {
        Optional<User> userOptional = userRepository.findByVerificationCode(token);
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Lỗi: Mã xác nhận không hợp lệ!");
        }

        User user = userOptional.get();
        user.setActive(true);
        user.setVerificationCode(null);
        userRepository.save(user);

        String role = user.getRole() != null ? user.getRole().name() : "";
        String roleName = "";
        switch (role) {
            case "ADMIN":
                roleName = "Quản trị viên";
                break;
            case "TEACHER":
                roleName = "Giáo viên";
                break;
            case "STUDENT":
                roleName = "Học sinh";
                break;
        }

        String loginLink = frontendUrl + "/login";
        Context context = new Context();
        context.setVariable("fullName", user.getFullName());
        context.setVariable("roleName", roleName);
        context.setVariable("email", user.getEmail());
        context.setVariable("loginLink", loginLink);
        emailService.sendHtmlMailAsync(user.getEmail(), "Kích hoạt tài khoản thành công", "auth-welcome", context);

        return new MessageResponse("Tài khoản đã được kích hoạt thành công!");
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        
        // Luôn trả về thành công để tránh User Enumeration
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Generate secure token
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            
            user.setResetPasswordToken(token);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            
            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("resetLink", resetLink);
            emailService.sendHtmlMailAsync(user.getEmail(), "Yêu cầu khôi phục mật khẩu MathClass", "forgot-password", context);
        }
        
        return new MessageResponse("Nếu email hợp lệ, một liên kết đặt lại mật khẩu đã được gửi đến hộp thư của bạn.");
    }

    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByResetPasswordToken(request.getToken());
        
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Token không hợp lệ hoặc đã hết hạn.");
        }
        
        User user = userOptional.get();
        
        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token không hợp lệ hoặc đã hết hạn.");
        }
        
        user.setPassword(encoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);
        
        return new MessageResponse("Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập bằng mật khẩu mới.", user.getRole().name());
    }

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Override
    public JwtResponse authenticateWithGoogle(GoogleAuthRequest request) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(request.getCredential());
            HttpEntity<String> entity = new HttpEntity<>("parameters",
                    headers);

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
                    if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                        user.setAvatarUrl(pictureUrl);
                        userRepository.save(user);
                    }
                } else {
                    user = new User();
                    user.setEmail(email);
                    user.setFullName(name);
                    user.setAvatarUrl(pictureUrl);
                    user.setActive(true);

                    Role role = Role.STUDENT; // Default
                    if (request.getRole() != null) {
                        try {
                            role = Role.valueOf(request.getRole().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            // ignore
                        }
                    }
                    user.setRole(role);
                    user.setPassword(encoder.encode(UUID.randomUUID().toString()));
                    user.setPhoneNumber(""); // Hoặc set null nếu cho phép nullable
                    userRepository.save(user);

                    NotificationSettings settings = NotificationSettings.builder()
                            .userId(user.getId())
                            .build();
                    notificationSettingsRepository.save(settings);
                }

                CustomUserDetails userDetails = CustomUserDetails.build(user);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String jwt = jwtUtils.generateJwtToken(authentication);

                return new JwtResponse(jwt,
                        userDetails.getId(),
                        userDetails.getEmail(),
                        userDetails.getFullName(),
                        userDetails.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .findFirst()
                                .map(r -> r.replace("ROLE_", ""))
                                .orElse(""),
                        userDetails.getAvatarUrl());

            } else {
                throw new BadRequestException("Token xác thực Google không hợp lệ.");
            }
        } catch (Exception e) {
            throw new BadRequestException("Lỗi xác thực Google: " + e.getMessage());
        }
    }
}
