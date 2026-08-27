# Specification: Student Group Chat & Student-to-Student Private Chat (`MathClass-service`)

## 1. Executive Summary & Objectives

Tính năng này cung cấp kênh trao đổi **Chat nhóm Lớp học** và **Chat riêng 1-1 giữa Học sinh với Học sinh** trong cùng một Lớp học (`classId`).

### Các mục tiêu chính:
1. **Chat nhóm Lớp học:** Tất cả học sinh và giáo viên trong lớp đều có thể gửi và nhận tin nhắn nhóm thời gian thực.
2. **Chat riêng Học sinh - Học sinh:** Học sinh có thể nhắn tin 1-1 riêng với bất kỳ bạn học nào thuộc cùng một lớp học.
3. **Trạng thái Trực tuyến Real-time (Presence):** Sử dụng `UserPresenceRegistry` theo dõi kết nối/ngắt kết nối WebSocket của tài khoản trên hệ thống, phát tin tới kênh STOMP `/topic/presence`.
4. **Phòng chống lỗi N+1 Query & Indexing:** JPQL DTO Constructor Projection lấy lịch sử tin nhắn chỉ trong **1 câu SQL duy nhất**, tối ưu hóa truy vấn với Composite Index.

---

## 2. Acceptance Criteria Checklist (AC)

- [x] **AC-BE-01:** Mở rộng bảng `chat_messages` hỗ trợ `recipient_id` (nullable) và `chat_type` (`CLASS_GROUP`, `DIRECT_STUDENT`, `DIRECT_TEACHER`).
- [x] **AC-BE-02:** Tạo Composite Index `(class_id, chat_type, created_at DESC)` và `(class_id, sender_id, recipient_id, created_at DESC)`.
- [x] **AC-BE-03:** Rest API `GET /api/v1/classes/{classCode}/chat/group/messages` lấy lịch sử tin nhắn chat nhóm lớp.
- [x] **AC-BE-04:** Rest API `GET /api/v1/classes/{classCode}/chat/direct/{otherUserId}/messages` lấy lịch sử chat 1-1 giữa 2 học sinh.
- [x] **AC-BE-05:** STOMP Controller `@MessageMapping("/chat.sendGroup")` xử lý nhận & broadcast tin nhắn nhóm tới `/topic/classroom/{classId}/group`.
- [x] **AC-BE-06:** STOMP Controller `@MessageMapping("/chat.sendDirect")` xử lý nhận & gửi tin nhắn 1-1 tới `/topic/classroom/{classId}/direct/{recipientId}` và người gửi.
- [x] **AC-BE-07:** Tích hợp `UserPresenceRegistry`: Theo dõi và phát thông báo Online/Offline tới `/topic/presence`.
- [x] **AC-BE-08:** Rest API `GET /api/v1/classes/{classCode}/chat/unread-summary` tính toán chính xác `groupUnreadCount` (truy vấn SQL $O(1)$) và `studentUnreadCounts` cho toàn bộ các kênh chat.

---

## 3. Database Schema & Index Specification

```sql
ALTER TABLE chat_messages 
ADD COLUMN recipient_id BIGINT NULL,
ADD COLUMN chat_type VARCHAR(30) NOT NULL DEFAULT 'DIRECT_TEACHER';

ALTER TABLE chat_messages 
ADD CONSTRAINT fk_chat_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_chat_messages_group 
ON chat_messages (class_id, chat_type, created_at DESC);

CREATE INDEX idx_chat_messages_direct 
ON chat_messages (class_id, sender_id, recipient_id, created_at DESC);
```

---

## 4. DTO Specifications

### 4.1. `GroupChatMessageRequest.java`
```java
package com.codegym.mathclass.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupChatMessageRequest(
    @NotNull(message = "ID lớp học không được để trống")
    Long classId,

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
    String content
) {}
```

### 4.2. `DirectChatMessageRequest.java`
```java
package com.codegym.mathclass.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DirectChatMessageRequest(
    @NotNull(message = "ID lớp học không được để trống")
    Long classId,

    @NotNull(message = "ID người nhận không được để trống")
    Long recipientId,

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
    String content
) {}
```

---

## 5. REST API & WebSocket Protocol Endpoints

### 5.1. REST APIs (`ChatController.java`)
* `GET /api/v1/classes/{classCode}/chat/group/messages` (Params: `page`, `size`)
* `GET /api/v1/classes/{classCode}/chat/direct/{otherUserId}/messages` (Params: `page`, `size`)
* `PUT /api/v1/classes/{classCode}/chat/read` (Params: `chatType`, `otherUserId`)

### 5.2. WebSocket / STOMP (`ChatStompController.java`)
* Outbound Destinations: `/app/chat.sendGroup`, `/app/chat.sendDirect`
* Inbound Channels:
  * Chat Nhóm: `/topic/classroom/{classId}/group`
  * Chat Riêng: `/topic/classroom/{classId}/direct/{userId}`
  * Presence: `/topic/presence`
