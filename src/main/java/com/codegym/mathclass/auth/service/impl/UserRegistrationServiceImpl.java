package com.codegym.mathclass.auth.service.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.service.UserRegistrationService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserRepository userRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final AiCreditService aiCreditService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageResponse registerUser(SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new BadRequestException("Lỗi: Email đã tồn tại!");
        }

        Role requestedRole = signUpRequest.getRole();
        if (requestedRole == Role.ADMIN) {
            throw new BadRequestException("Lỗi đăng ký tài khoản");
        }

        String token = UUID.randomUUID().toString();
        User user = User.builder()
                .email(signUpRequest.getEmail())
                .fullName(signUpRequest.getFullName())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .password(encoder.encode(signUpRequest.getPassword()))
                .role(requestedRole != null ? requestedRole : Role.STUDENT)
                .verificationCode(token)
                .build();

        userRepository.save(user);

        aiCreditService.grantDefaultForNewUser(user.getId(), user.getRole());

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
    @Transactional(rollbackFor = Exception.class)
    public MessageResponse verifyUser(String token) {
        Optional<User> userOptional = userRepository.findByVerificationCode(token);
        if (userOptional.isEmpty()) {
            throw new BadRequestException("Lỗi: Mã xác nhận không hợp lệ!");
        }

        User user = userOptional.get();
        user.setActive(true);
        user.setVerificationCode(null);
        userRepository.save(user);

        String roleName = user.getRole() != null ? user.getRole().getDisplayName() : "";

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
    @Transactional(rollbackFor = Exception.class)
    public User registerOAuth2User(String email, String fullName, String pictureUrl, Role role) {
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .avatarUrl(pictureUrl)
                .isActive(true)
                .role(role != null ? role : Role.STUDENT)
                .provider(Provider.GOOGLE)
                .password(null)
                .phoneNumber("")
                .build();

        user = userRepository.save(user);

        aiCreditService.grantDefaultForNewUser(user.getId(), user.getRole());

        NotificationSettings settings = NotificationSettings.builder()
                .userId(user.getId())
                .build();
        notificationSettingsRepository.save(settings);

        return user;
    }
}
