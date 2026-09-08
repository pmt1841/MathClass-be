package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component("compositeNotificationStrategy")
@RequiredArgsConstructor
@Slf4j
public class CompositeNotificationStrategy implements NotificationStrategy {

    private final SseNotificationStrategy sseStrategy;
    private final EmailNotificationStrategy emailStrategy;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.COMPOSITE;
    }

    @Override
    public boolean isUserOnline(Long userId) {
        return sseStrategy.isUserOnline(userId);
    }

    @Override
    @Async
    public void send(NotificationPayload payload) {
        if (payload == null || payload.recipientId() == null) {
            return;
        }

        Long recipientId = payload.recipientId();
        if (sseStrategy.isUserOnline(recipientId)) {
            log.info("[CompositeStrategy] User {} đang ONLINE -> Gửi qua SSE Stream", recipientId);
            sseStrategy.send(payload);
        } else {
            log.info("[CompositeStrategy] User {} đang OFFLINE -> Fallback tự động sang gửi Email", recipientId);
            emailStrategy.send(payload);
        }
    }
}
