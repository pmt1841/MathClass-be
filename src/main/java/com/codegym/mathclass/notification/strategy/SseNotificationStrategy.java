package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component("sseNotificationStrategy")
@Slf4j
public class SseNotificationStrategy implements NotificationStrategy {

    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 mins
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SSE;
    }

    @Override
    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        List<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null && !userEmitters.isEmpty();
    }

    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    @Override
    public void send(NotificationPayload payload) {
        if (payload == null || payload.recipientId() == null) {
            return;
        }

        Long userId = payload.recipientId();
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("[SseStrategy] User {} không có kết nối SSE active nào", userId);
            return;
        }

        String eventName = payload.eventName() != null && !payload.eventName().isBlank()
                ? payload.eventName()
                : "NOTIFICATION";
        Object eventData = payload.data() != null ? payload.data() : payload;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(eventData));
            } catch (IOException e) {
                log.warn("[SseStrategy] Lỗi khi gửi SSE event {} cho user {}: {}", eventName, userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }

    public void removeEmitter(Long userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        emitters.computeIfPresent(userId, (k, userEmitters) -> {
            userEmitters.remove(emitter);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }

    public int getActiveEmitterCount(Long userId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null ? userEmitters.size() : 0;
    }
}
