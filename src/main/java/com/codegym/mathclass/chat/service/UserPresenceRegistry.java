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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class UserPresenceRegistry {

    private final Set<Long> onlineUserIds = ConcurrentHashMap.newKeySet();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                onlineUserIds.add(userDetails.getId());
                log.info("User connected WebSocket STOMP: userId={}, onlineCount={}", userDetails.getId(), onlineUserIds.size());
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                onlineUserIds.remove(userDetails.getId());
                log.info("User disconnected WebSocket STOMP: userId={}, onlineCount={}", userDetails.getId(), onlineUserIds.size());
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        return userId != null && onlineUserIds.contains(userId);
    }

    public Set<Long> getOnlineUserIds() {
        return Collections.unmodifiableSet(onlineUserIds);
    }
}
