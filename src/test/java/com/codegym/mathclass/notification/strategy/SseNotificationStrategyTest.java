package com.codegym.mathclass.notification.strategy;

import com.codegym.mathclass.notification.dto.NotificationChannel;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class SseNotificationStrategyTest {

    private SseNotificationStrategy sseStrategy;

    @BeforeEach
    void setUp() {
        sseStrategy = new SseNotificationStrategy();
    }

    @Test
    @DisplayName("Kiểm tra supports chỉ trả về true với channel SSE")
    void supports_ChannelCheck() {
        assertTrue(sseStrategy.supports(NotificationChannel.SSE));
        assertFalse(sseStrategy.supports(NotificationChannel.EMAIL));
        assertFalse(sseStrategy.supports(NotificationChannel.COMPOSITE));
    }

    @Test
    @DisplayName("isUserOnline trả về false khi user chưa có kết nối SSE nào")
    void isUserOnline_UserNotConnected_ReturnsFalse() {
        assertFalse(sseStrategy.isUserOnline(100L));
        assertFalse(sseStrategy.isUserOnline(null));
    }

    @Test
    @DisplayName("Tạo emitter thành công và isUserOnline trả về true")
    void createEmitter_ValidUser_AddsEmitterAndOnlineTrue() {
        SseEmitter emitter = sseStrategy.createEmitter(1L);

        assertNotNull(emitter);
        assertTrue(sseStrategy.isUserOnline(1L));
        assertEquals(1, sseStrategy.getActiveEmitterCount(1L));
    }

    @Test
    @DisplayName("Xóa emitter khi ngắt kết nối sẽ giảm active count và cập nhật isUserOnline")
    void removeEmitter_RemovesEmitterCorrectly() {
        SseEmitter emitter = sseStrategy.createEmitter(1L);
        assertTrue(sseStrategy.isUserOnline(1L));

        sseStrategy.removeEmitter(1L, emitter);

        assertFalse(sseStrategy.isUserOnline(1L));
        assertEquals(0, sseStrategy.getActiveEmitterCount(1L));
    }

    @Test
    @DisplayName("Gửi thông báo thành công cho user đang online")
    void send_OnlineUser_SendsSseEvent() {
        sseStrategy.createEmitter(1L);
        NotificationPayload payload = new NotificationPayload(
                1L, "Tiêu đề", "Nội dung", "/link", "TEST_EVENT", "Data"
        );

        assertDoesNotThrow(() -> sseStrategy.send(payload));
    }

    @Test
    @DisplayName("Gửi thông báo cho null payload hoặc recipientId null sẽ bỏ qua không bị crash")
    void send_NullPayload_GracefullyIgnored() {
        assertDoesNotThrow(() -> sseStrategy.send(null));
        assertDoesNotThrow(() -> sseStrategy.send(new NotificationPayload(null, "T", "M", "L", "E", null)));
    }
}
