# Specification: Student - Teacher Private Realtime Chat (`MathClass-service`)

## 1. Executive Summary & Objectives

Tính năng này cung cấp kênh trao đổi riêng thời gian thực (1-1 Private Chat) giữa **Học sinh** và **Giảng viên** phụ trách trong từng **Lớp học** (`classId`).

### Các mục tiêu chính:
1. **Chat riêng 1-1 trong Lớp học:** Mỗi lớp học sinh chỉ nhắn tin 1-1 với duy nhất Giảng viên phụ trách lớp học đó. Giảng viên có thể chọn từng học sinh trong danh sách của lớp để xem và phản hồi tin nhắn.
2. **Thời gian thực (Real-time):** Sử dụng WebSocket STOMP Broker (Spring Boot WebSocket) để nhận/gửi tin tức thì với độ trễ <50ms.
3. **Hiển thị Công thức Toán học (KaTeX):** Nội dung tin nhắn hỗ trợ định dạng văn bản + công thức LaTeX.
4. **Hiệu năng & Chống lỗi N+1 Query:** Đảm bảo sử dụng JPQL DTO Constructor Projection (`SELECT new ChatMessageResponse(...)`) để truy vấn phân trang lịch sử tin nhắn chỉ trong **1 câu SQL duy nhất**, tuyệt đối không bị lỗi Lazy Loading N+1. Index Scan trong PostgreSQL.

---

## 2. Acceptance Criteria Checklist (AC)

- [ ] **AC-BE-01:** Bảng `chat_messages` được tạo đúng cấu trúc kèm Composite Index `(class_id, student_id, created_at DESC)`.
- [ ] **AC-BE-02:** Cấu hình WebSocket STOMP Endpoint `/ws-chat` và kênh Broadcast `/topic/classroom/{classId}/student/{studentId}`.
- [ ] **AC-BE-03:** `WebSocketSecurityInterceptor` trích xuất và xác thực JWT Token từ khung STOMP `CONNECT`.
- [ ] **AC-BE-04:** API `GET /api/v1/classes/{classCode}/chat/messages` lấy lịch sử tin nhắn phân trang (trả về DTO duy nhất 1 SQL query).
- [ ] **AC-BE-05:** API `PUT /api/v1/classes/{classCode}/chat/messages/read` đánh dấu tất cả tin nhắn trong room là đã đọc (`is_read = true`).
- [ ] **AC-BE-06:** STOMP Controller `@MessageMapping("/chat.send")` xử lý nhận tin nhắn, lưu vào DB và phát sóng tới subscriber thời gian thực.
- [ ] **AC-BE-07:** Phân quyền chặt chẽ: Chỉ Học sinh trong lớp hoặc Giảng viên dạy lớp đó mới có quyền truy cập hoặc nhận/gửi tin nhắn trong room chat tương ứng.

---

## 3. Database Schema & Index Specification

```sql
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_messages_class FOREIGN KEY (class_id) REFERENCES classrooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_class_student_created 
ON chat_messages (class_id, student_id, created_at DESC);
```

---

## 4. DTO Specifications

### 4.1. `ChatMessageRequest.java`
```java
package com.codegym.mathclass.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
    @NotNull(message = "ID lớp học không được để trống")
    Long classId,

    @NotNull(message = "ID học sinh không được để trống")
    Long studentId,

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
    String content
) {}
```

### 4.2. `ChatMessageResponse.java`
```java
package com.codegym.mathclass.chat.dto;

import java.time.Instant;

public record ChatMessageResponse(
    Long id,
    Long classId,
    Long studentId,
    Long senderId,
    String senderName,
    String senderAvatar,
    String content,
    Boolean isRead,
    Instant createdAt
) {}
```

---

## 5. JPA Repository & N+1 Prevention Query

```java
package com.codegym.mathclass.chat.repository;

import com.codegym.mathclass.chat.dto.ChatMessageResponse;
import com.codegym.mathclass.chat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        SELECT new com.codegym.mathclass.chat.dto.ChatMessageResponse(
            m.id, 
            m.classId, 
            m.studentId, 
            m.sender.id, 
            m.sender.fullName, 
            m.sender.avatarUrl, 
            m.content, 
            m.isRead, 
            m.createdAt
        )
        FROM ChatMessage m 
        JOIN m.sender
        WHERE m.classId = :classId AND m.studentId = :studentId
        ORDER BY m.createdAt DESC
        """)
    Page<ChatMessageResponse> findHistoryByClassIdAndStudentId(
        @Param("classId") Long classId,
        @Param("studentId") Long studentId,
        Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE ChatMessage m 
        SET m.isRead = true 
        WHERE m.classId = :classId 
          AND m.studentId = :studentId 
          AND m.sender.id != :currentUserId 
          AND m.isRead = false
        """)
    int markMessagesAsRead(
        @Param("classId") Long classId, 
        @Param("studentId") Long studentId, 
        @Param("currentUserId") Long currentUserId
    );
}
```

---

## 6. REST API & WebSocket Protocol Endpoints

### 6.1. REST APIs (`ChatController.java`)
* `GET /api/v1/classes/{classCode}/chat/messages`
  * Params: `studentId` (Long), `page` (default 0), `size` (default 20)
  * Returns: `ApiResponse<Page<ChatMessageResponse>>`
* `PUT /api/v1/classes/{classCode}/chat/messages/read`
  * Params: `studentId` (Long)
  * Returns: `ApiResponse<Void>`

### 6.2. WebSocket / STOMP (`ChatStompController.java`)
* Endpoint: `/ws-chat`
* Outbound Destination: `/app/chat.send`
* Inbound Broadcast Channel: `/topic/classroom/{classId}/student/{studentId}`

---

## 7. Unit Test Cases Checklist

- [ ] **UT-BE-01:** `findHistory_success` - Lấy đúng lịch sử tin nhắn phân trang, thực thi duy nhất 1 câu SQL lệnh (0 lỗi N+1).
- [ ] **UT-BE-02:** `sendMessage_success` - Gửi tin nhắn hợp lệ, lưu vào DB và phát tin thành công qua `SimpMessagingTemplate`.
- [ ] **UT-BE-03:** `sendMessage_fail_unauthorized` - Người dùng không thuộc lớp học bị từ chối gửi tin nhắn.
- [ ] **UT-BE-04:** `markAsRead_success` - Đánh dấu các tin nhắn chưa đọc của đối phương thành `is_read = true`.
