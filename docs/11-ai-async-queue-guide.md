# Hướng Dẫn Kỹ Thuật: Hàng Đợi Tác Vụ AI Phân Tán Với Redisson (AI Async Queue Guide)

Tài liệu này mô tả chi tiết giải pháp xử lý bất đồng bộ các tác vụ AI tốn nhiều tài nguyên (Heavy AI Jobs) sử dụng **Redis Queue** và thư viện **Redisson** trong hệ thống **MathClass Backend**.

---

## 1. Bối Cảnh & Vấn Đề Cần Giải Quyết

Khi người dùng thực hiện các tác vụ AI phức tạp như:
- Tự động đánh giá tiến độ học sinh qua nhiều bài tập (`Student Remark AI Evaluation`).
- Chấm điểm tự động và nhận xét chi tiết toàn bộ bài làm của lớp học (`Batch AI Grading`).
- Sinh bộ đề bài tập lớn kèm lời giải và bản vẽ hình học (`Batch Problem Generation`).

Thời gian phản hồi từ các mô hình ngôn ngữ lớn (LLMs như GPT-4, Claude 3.5 Sonnet) có thể kéo dài từ **15 đến 60 giây**. Nếu xử lý đồng bộ (Synchronous HTTP):
1. Dễ dẫn tới lỗi **HTTP Connection Timeout (504 Gateway Timeout)** trên Gateway/Proxy (Nginx, Cloudflare).
2. Làm nghẽn Thread Pool của Tomcat Server, khiến hệ thống không thể xử lý các tác vụ thông thường khác.
3. Không thể áp dụng cơ chế tự động thử lại (Auto-Retry with Backoff) khi gặp lỗi Rate Limit (HTTP 429) từ nhà cung cấp AI.

---

## 2. Mô Hình Kiến Trúc Producer - Consumer (Redisson Queue)

```mermaid
flowchart LR
    Client[Client / Teacher] -->|POST ?async=true| Controller[StudentRemarkController / AI Controllers]
    Controller -->|Tạm giữ Credit & Đẩy Job| Queue[Redisson RBlockingQueue]
    Controller -->>|Trả HTTP 202 kèm JobId| Client
    
    subgraph Background Worker Pool
        Worker1[AI Job Worker #1]
        Worker2[AI Job Worker #2]
    end
    
    Queue -->|takeJob| Worker1
    Queue -->|takeJob| Worker2
    
    Worker1 -->|Gọi LLM API| LLM[OpenAI / Gemini / Anthropic]
    Worker1 -->|Lưu kết quả & Trừ Credit thật| DB[(PostgreSQL & Redis Status)]
    
    Client -.->|Polling GET /api/v1/ai/jobs/jobId| Controller
```

---

## 3. Các Trạng Thái Vòng Đời Của AI Job (`AiJobStatus`)

Một tác vụ AI trải qua các trạng thái sau được lưu trữ và cập nhật trong Redis:

```
[QUEUED] ──> [PROCESSING] ──┬──> [COMPLETED] (Thành công - trừ credit chính thức)
                            ├──> [RETRYING] ──> (Thử lại tối đa 3 lần nếu lỗi mạng/429)
                            ├──> [FAILED]    (Thất bại - tự động hoàn cọc 100% Credit)
                            └──> [CANCELLED] (Người dùng chủ động hủy)
```

1. **`QUEUED`**: Tác vụ đã được đẩy vào hàng đợi Redis `RBlockingQueue`, số Credit dự kiến đã được khóa tạm thời (Reserve).
2. **`PROCESSING`**: Một Worker Thread đã nhận tác vụ và đang gọi API của AI Provider.
3. **`RETRYING`**: Gặp sự cố mạng hoặc lỗi quá tải tạm thời từ AI Provider, Worker đưa job vào `RDelayedQueue` để thử lại sau vài giây với cơ chế Exponential Backoff.
4. **`COMPLETED`**: AI hoàn tất trả về kết quả, kết quả được lưu vào database và số Credit tạm giữ được ghi nhận thành giao dịch tiêu thụ thực tế.
5. **`FAILED`**: Gặp lỗi không thể phục hồi hoặc vượt quá số lần thử lại tối đa. Hệ thống tự động **Hoàn trả 100% số Credit** đã tạm giữ cho người dùng.
6. **`CANCELLED`**: Người dùng chủ động gửi yêu cầu hủy (`POST /api/v1/ai/jobs/{jobId}/cancel`). Nếu job vẫn còn ở trạng thái `QUEUED`, credit sẽ được hoàn trả ngay lập tức.

---

## 4. Cơ Chế Khóa Phân Tán (Distributed Locking)

Để đảm bảo an toàn dữ liệu và tránh tình trạng nhiều Worker xử lý trùng một Job hoặc tranh chấp cập nhật số dư Credit, hệ thống áp dụng:
- **`RLock` (Redisson Distributed Lock):** Khóa tài nguyên theo `jobId` và `userId` khi xử lý hoàn tất hoặc hoàn tiền.
- Thời gian chờ khóa (Wait time) và thời gian tự động giải phóng (Lease time) được cấu hình chặt chẽ để chống Deadlock.

---

## 5. Danh Sách API Tra Cứu Hàng Đợi

Toàn bộ APIs nằm tại tiền tố `/api/v1/ai/jobs`:

| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/ai/jobs/{jobId}` | Authenticated | Tra cứu tiến độ xử lý và kết quả của Job. Người dùng chỉ xem được job của mình, Quản trị viên (`ADMIN`) có thể xem toàn bộ. |
| `POST` | `/api/v1/ai/jobs/{jobId}/cancel` | Authenticated | Yêu cầu dừng và hủy Job. Hỗ trợ query param `?force=true` dành cho Quản trị viên. |
