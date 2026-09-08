package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeNotificationStrategyTest {

    @Mock
    private SseNotificationStrategy sseStrategy;

    @Mock
    private EmailNotificationStrategy emailStrategy;

    private CompositeNotificationStrategy compositeStrategy;

    @BeforeEach
    void setUp() {
        compositeStrategy = new CompositeNotificationStrategy(sseStrategy, emailStrategy);
    }

    @Test
    @DisplayName("Kiểm tra supports chỉ trả về true với channel COMPOSITE")
    void supports_ChannelCheck() {
        assertTrue(compositeStrategy.supports(NotificationChannel.COMPOSITE));
        assertFalse(compositeStrategy.supports(NotificationChannel.SSE));
        assertFalse(compositeStrategy.supports(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("Khi user ONLINE -> Gửi qua SSE Strategy và KHÔNG gọi Email Strategy")
    void send_UserOnline_RoutesToSseOnly() {
        when(sseStrategy.isUserOnline(1L)).thenReturn(true);

        NotificationPayload payload = new NotificationPayload(
                1L, "Title", "Message", "/link", "EVENT", null
        );

        compositeStrategy.send(payload);

        verify(sseStrategy, times(1)).send(payload);
        verify(emailStrategy, never()).send(any());
    }

    @Test
    @DisplayName("Khi user OFFLINE -> Tự động Fallback gửi qua Email Strategy và KHÔNG gọi SSE Strategy send")
    void send_UserOffline_RoutesToEmailFallbackOnly() {
        when(sseStrategy.isUserOnline(2L)).thenReturn(false);

        NotificationPayload payload = new NotificationPayload(
                2L, "Title", "Message", "/link", "EVENT", null
        );

        compositeStrategy.send(payload);

        verify(emailStrategy, times(1)).send(payload);
        verify(sseStrategy, never()).send(any());
    }
}
