package com.codegym.mathclass.chat.config;

import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.security.jwt.JwtUtils;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.security.services.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final ClassroomRepository classroomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                UsernamePasswordAuthenticationToken authentication = null;

                // 1. Lấy từ Session Attributes do HandshakeInterceptor nạp từ HttpOnly Cookie
                if (accessor.getSessionAttributes() != null && accessor.getSessionAttributes().containsKey("USER_AUTH")) {
                    authentication = (UsernamePasswordAuthenticationToken) accessor.getSessionAttributes().get("USER_AUTH");
                }

                // 2. Fallback nếu client truyền header Authorization/Bearer
                if (authentication == null) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    String token = null;
                    if (authorization != null && !authorization.isEmpty()) {
                        String bearerToken = authorization.get(0);
                        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                            token = bearerToken.substring(7);
                        }
                    }
                    if (token == null || token.isBlank()) {
                        List<String> tokenParams = accessor.getNativeHeader("token");
                        if (tokenParams != null && !tokenParams.isEmpty()) {
                            token = tokenParams.get(0);
                        }
                    }

                    if (token != null && jwtUtils.validateJwtToken(token)) {
                        String username = jwtUtils.getUserNameFromJwtToken(token);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                    }
                }

                if (authentication != null) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    accessor.setUser(authentication);
                    log.info("WebSocket STOMP Connection authenticated for user: {}", authentication.getName());
                } else {
                    log.warn("WebSocket STOMP Connection missing or invalid JWT Token");
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                // Phân quyền SUBSCRIBE topic - Chống lỗ hổng nghe lén IDOR
                validateTopicSubscription(accessor);
            }
        }
        return message;
    }

    private void validateTopicSubscription(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            log.error("StompCommand.SUBSCRIBE rejected: User is not authenticated");
            throw new AccessDeniedException("Bạn chưa đăng nhập không thể đăng ký channel");
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((Authentication) principal).getPrincipal();
        String destination = accessor.getDestination();

        if (destination != null && destination.startsWith("/topic/classroom/")) {
            String[] parts = destination.split("/");
            // Kênh chat học sinh: /topic/classroom/{classId}/student/{studentId}
            if (destination.contains("/student/")) {
                if (parts.length >= 6) {
                    try {
                        long classId = Long.parseLong(parts[3]);
                        long studentId = Long.parseLong(parts[5]);

                        boolean isSelfStudent = userDetails.getId() == studentId;
                        boolean isClassTeacher = classroomRepository.findById(classId)
                                .map(c -> c.getTeacher().getId() == userDetails.getId())
                                .orElse(false);

                        if (!isSelfStudent && !isClassTeacher) {
                            log.error("User {} attempted unauthorized STOMP subscription to {}", userDetails.getUsername(), destination);
                            throw new AccessDeniedException("Bạn không có quyền đăng ký channel tin nhắn này");
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid STOMP destination format: {}", destination);
                    }
                }
            } 
            // Kênh giảng viên nhận thông báo: /topic/classroom/{classId}/teacher
            else if (destination.endsWith("/teacher")) {
                if (parts.length >= 5) {
                    try {
                        long classId = Long.parseLong(parts[3]);
                        boolean isClassTeacher = classroomRepository.findById(classId)
                                .map(c -> c.getTeacher().getId() == userDetails.getId())
                                .orElse(false);

                        if (!isClassTeacher) {
                            log.error("User {} attempted unauthorized STOMP subscription to teacher destination {}", userDetails.getUsername(), destination);
                            throw new AccessDeniedException("Chỉ giảng viên phụ trách lớp mới được đăng ký channel này");
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid STOMP destination format: {}", destination);
                    }
                }
            }
        }
    }
}
