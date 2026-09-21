package com.codegym.mathclass.chat.service;

import com.codegym.mathclass.chat.dto.event.PresenceMessage;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UserPresenceRegistry {

    public static final String ONLINE_USERS_KEY = "presence:online:users";
    public static final String USER_SESSIONS_PREFIX = "presence:user:";
    public static final String SESSION_LOOKUP_PREFIX = "presence:session:";
    public static final String PRESENCE_TOPIC_NAME = "presence:events";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final RedissonClient redissonClient;
    private final String instanceId = UUID.randomUUID().toString();
    private int presenceListenerId = -1;

    public UserPresenceRegistry(
            @Lazy SimpMessagingTemplate messagingTemplate,
            @Lazy UserRepository userRepository,
            RedissonClient redissonClient
    ) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void initSubscriber() {
        try {
            RTopic topic = redissonClient.getTopic(PRESENCE_TOPIC_NAME, new TypedJsonJacksonCodec(PresenceMessage.class));
            presenceListenerId = topic.addListener(PresenceMessage.class, (channel, msg) -> {
                if (msg != null && !instanceId.equals(msg.originInstanceId())) {
                    log.debug("Received presence event from cluster: userId={}, isOnline={}", msg.userId(), msg.isOnline());
                    sendLocalPresence(msg.userId(), msg.isOnline(), msg.lastActiveAt());
                }
            });
            log.info("Initialized Redis presence topic listener on '{}' with instanceId={}", PRESENCE_TOPIC_NAME, instanceId);
        } catch (Exception ex) {
            log.warn("Failed to register Redis presence topic listener: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (presenceListenerId != -1) {
            try {
                redissonClient.getTopic(PRESENCE_TOPIC_NAME, new TypedJsonJacksonCodec(PresenceMessage.class)).removeListener(presenceListenerId);
                log.info("Removed Redis presence topic listener for instanceId={}", instanceId);
            } catch (Exception ex) {
                log.debug("Error removing presence listener on shutdown: {}", ex.getMessage());
            }
        }
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                Long userId = userDetails.getId();
                if (sessionId != null) {
                    RSet<String> sessions = redissonClient.getSet(USER_SESSIONS_PREFIX + userId + ":sessions", StringCodec.INSTANCE);
                    sessions.add(sessionId);
                    sessions.expire(Duration.ofHours(24));

                    RBucket<String> sessionBucket = redissonClient.getBucket(SESSION_LOOKUP_PREFIX + sessionId, StringCodec.INSTANCE);
                    sessionBucket.set(userId.toString(), Duration.ofHours(24));

                    RSet<String> onlineUsers = redissonClient.getSet(ONLINE_USERS_KEY, StringCodec.INSTANCE);
                    boolean isFirstSession = onlineUsers.add(userId.toString());

                    log.info("User connected WebSocket STOMP: userId={}, sessionId={}, totalSessions={}",
                            userId, sessionId, sessions.size());

                    LocalDateTime now = LocalDateTime.now();
                    try {
                        userRepository.updateLastActiveAt(userId, now);
                    } catch (Exception ex) {
                        log.warn("Failed to update lastActiveAt on connect: {}", ex.getMessage());
                    }

                    if (isFirstSession) {
                        broadcastPresence(userId, true, now);
                    }
                }
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Long userId = null;

        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                userId = userDetails.getId();
            }
        }

        if (userId == null && sessionId != null) {
            try {
                RBucket<String> sessionBucket = redissonClient.getBucket(SESSION_LOOKUP_PREFIX + sessionId, StringCodec.INSTANCE);
                String val = sessionBucket.get();
                if (val != null) {
                    userId = Long.parseLong(val);
                }
            } catch (Exception ex) {
                log.debug("Error looking up userId for disconnected session: {}", ex.getMessage());
            }
        }

        if (sessionId != null) {
            try {
                redissonClient.getBucket(SESSION_LOOKUP_PREFIX + sessionId, StringCodec.INSTANCE).delete();
            } catch (Exception ex) {
                log.debug("Error deleting session lookup bucket: {}", ex.getMessage());
            }
        }

        if (userId != null && sessionId != null) {
            RSet<String> sessions = redissonClient.getSet(USER_SESSIONS_PREFIX + userId + ":sessions", StringCodec.INSTANCE);
            sessions.remove(sessionId);

            if (sessions.isEmpty()) {
                sessions.delete();
                RSet<String> onlineUsers = redissonClient.getSet(ONLINE_USERS_KEY, StringCodec.INSTANCE);
                boolean removed = onlineUsers.remove(userId.toString());

                log.info("User completely disconnected all WebSocket sessions: userId={}", userId);
                LocalDateTime now = LocalDateTime.now();
                try {
                    userRepository.updateLastActiveAt(userId, now);
                } catch (Exception ex) {
                    log.warn("Failed to update lastActiveAt on disconnect: {}", ex.getMessage());
                }

                if (removed) {
                    broadcastPresence(userId, false, now);
                }
            } else {
                log.info("User disconnected one session: userId={}, remainingSessions={}", userId, sessions.size());
            }
        }
    }

    public void broadcastPresence(Long userId, boolean isOnline, LocalDateTime lastActiveAt) {
        if (userId == null) {
            return;
        }
        String lastActiveAtStr = lastActiveAt != null ? lastActiveAt.toString() : "";
        sendLocalPresence(userId, isOnline, lastActiveAtStr);

        try {
            RTopic topic = redissonClient.getTopic(PRESENCE_TOPIC_NAME, new TypedJsonJacksonCodec(PresenceMessage.class));
            topic.publish(new PresenceMessage(instanceId, userId, isOnline, lastActiveAtStr));
        } catch (Exception ex) {
            log.warn("Failed to publish presence event to Redis topic: {}", ex.getMessage());
        }
    }

    public void sendLocalPresence(Long userId, boolean isOnline, String lastActiveAtStr) {
        if (messagingTemplate != null && userId != null) {
            try {
                Map<String, Object> payload = Map.of(
                        "userId", userId,
                        "isOnline", isOnline,
                        "lastActiveAt", lastActiveAtStr != null ? lastActiveAtStr : ""
                );
                messagingTemplate.convertAndSend("/topic/presence", (Object) payload);
            } catch (Exception ex) {
                log.warn("Failed to broadcast presence locally for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    public void markLoggedOut(Long userId) {
        if (userId != null) {
            try {
                redissonClient.getSet(USER_SESSIONS_PREFIX + userId + ":sessions", StringCodec.INSTANCE).delete();
                RSet<String> onlineUsers = redissonClient.getSet(ONLINE_USERS_KEY, StringCodec.INSTANCE);
                boolean removed = onlineUsers.remove(userId.toString());
                if (removed) {
                    broadcastPresence(userId, false, LocalDateTime.now());
                }
            } catch (Exception ex) {
                log.warn("Failed to mark user logged out in Redis: {}", ex.getMessage());
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return redissonClient.getSet(ONLINE_USERS_KEY, StringCodec.INSTANCE).contains(userId.toString());
        } catch (Exception ex) {
            log.warn("Failed to check isUserOnline for userId={}: {}", userId, ex.getMessage());
            return false;
        }
    }

    public Set<Long> getOnlineUserIds() {
        try {
            RSet<String> onlineUsers = redissonClient.getSet(ONLINE_USERS_KEY, StringCodec.INSTANCE);
            Set<String> members = onlineUsers.readAll();
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            return members.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception ex) {
            log.warn("Failed to fetch getOnlineUserIds from Redis: {}", ex.getMessage());
            return Collections.emptySet();
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
