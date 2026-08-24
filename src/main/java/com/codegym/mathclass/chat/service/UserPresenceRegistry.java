package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class UserPresenceRegistry {

    // Mapping userId -> Set các sessionId của người dùng đó (xử lý mở nhiều tab)
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                Long userId = userDetails.getId();
                if (sessionId != null) {
                    userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
                    log.info("User connected WebSocket STOMP: userId={}, sessionId={}, activeSessions={}",
                            userId, sessionId, userSessions.get(userId).size());
                }
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                Long userId = userDetails.getId();
                if (sessionId != null && userSessions.containsKey(userId)) {
                    Set<String> sessions = userSessions.get(userId);
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        userSessions.remove(userId);
                        log.info("User completely disconnected all WebSocket sessions: userId={}", userId);
                    } else {
                        log.info("User disconnected one session: userId={}, remainingSessions={}", userId, sessions.size());
                    }
                }
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        return userId != null && userSessions.containsKey(userId) && !userSessions.get(userId).isEmpty();
    }

    public Set<Long> getOnlineUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }
}
