# Specification: Student Classmate List Feature (`MathClass-service` & `MathClass-fe`)

## 1. Executive Summary & Objectives

Tính năng **Xem danh sách học sinh trong lớp (Student Classmate List)** cho phép học viên (Student) khi truy cập vào trang chi tiết lớp học (`/classes/[classCode]/student`) có thể nhấn vào badge số lượng học sinh (`X/Y học sinh`) để mở ra một Modal hiển thị danh sách các bạn cùng lớp kèm theo trạng thái trực tuyến (Online/Offline).

### Mục tiêu chính:
- Hiển thị danh sách bạn học với thiết kế tối giản: `[Avatar] [Họ và tên] [Chấm trạng thái Online (Xanh) / Offline (Xám)]`.
- Hỗ trợ bộ lọc xem **Tất cả học sinh** hoặc **Chỉ học sinh Online**.
- Sắp xếp học sinh **Online lên phía trên**, **Offline phía dưới** khi ở chế độ hiển thị tất cả.
- Bảo mật thông tin cá nhân: Không hiển thị email, SĐT hay nút quản lý của giáo viên.

---

## 2. Acceptance Criteria Checklist (AC)

- [ ] **AC-01:** Tại banner trang chi tiết lớp học (`StudentClassDetailPageClient`), badge `X/Y học sinh` có thể click được (hiệu ứng hover & cursor pointer).
- [ ] **AC-02:** Click vào badge mở ra `ClassroomStudentsModal` (Dialog Popup).
- [ ] **AC-03:** Modal có thanh Tab lọc gồm 2 tùy chọn: `Tất cả` và `Chỉ Online`.
- [ ] **AC-04:** Mỗi thẻ/dòng học sinh hiển thị đúng cấu trúc gọn nhẹ:
  - Avatar tròn (ảnh đại diện hoặc chữ cái đầu tên).
  - Họ và tên đầy đủ (`fullName`).
  - Nút/chấm trạng thái: Màu xanh lá (`bg-emerald-500`) nếu `isOnline == true`, màu xám (`bg-slate-300`) nếu `isOnline == false`.
- [ ] **AC-05:** Ở Tab `Tất cả`, danh sách ưu tiên hiển thị toàn bộ học sinh Online trước, sau đó tới các học sinh Offline.
- [ ] **AC-06:** Ở Tab `Chỉ Online`, chỉ lọc danh sách có `isOnline == true`. Nếu không có ai online, hiển thị Empty State thích hợp.
- [ ] **AC-07:** Backend API `GET /api/v1/classrooms/{classCode}/students` (hoặc endpoint dành riêng cho student) trả về danh sách học sinh kèm trạng thái `isOnline` dựa trên thời gian tương tác `lastActiveAt` trong vòng 5 phút.

---

## 3. Decision Log

| STT | Quyết định | Phương án cân nhắc | Lý do chọn |
| :--- | :--- | :--- | :--- |
| 1 | Dùng Modal Dialog + REST API với cờ `isOnline` dựa trên `lastActiveAt` (5 phút) | WebSocket real-time / Polling 30s | Đảm bảo hiệu năng, YAGNI, không over-engineer, tận dụng REST API hiện có. |
| 2 | Thiết kế dòng gọn nhẹ `[Avatar] [Họ tên] [Chấm màu Online/Offline]` | Hiển thị thêm chữ "Đang hoạt động", "Truy cập X phút trước" | Tối ưu trải nghiệm giao diện hiện đại, tinh gọn, tránh rườm rà. |
| 3 | Sắp xếp ưu tiên Online ở trên | Sắp xếp theo tên ABC ngẫu nhiên | Giúp học viên nhanh chóng nhận biết bạn học đang trực tuyến. |

---

## 4. Technical Architecture

### 4.1. Backend (`MathClass-service`)
- **User Activity Tracking**: Mỗi khi người dùng thực hiện yêu cầu tới backend, tự động cập nhật `user.lastActiveAt = LocalDateTime.now()`.
- **DTO Enhancement (`StudentResponse` / `ClassmateResponse`)**:
  - Thêm trường `boolean isOnline;`
  - Calculation: `isOnline = lastActiveAt != null && lastActiveAt.isAfter(LocalDateTime.now().minusMinutes(5))`
- **Controller & Service**:
  - API `GET /api/v1/classrooms/{classCode}/students` trả về danh sách được sắp xếp: `isOnline DESC`, `fullName ASC`.

### 4.2. Frontend (`MathClass-fe`)
- **Component mới**: `ClassroomStudentsModal.tsx` tại `app/(dashboard)/classes/[classCode]/student/_components/ClassroomStudentsModal.tsx`.
- **Trigger Component**: Cập nhật badge trong `student-client.tsx` để mở modal state (`isStudentsModalOpen`).
- **Hook**: Tận dụng `useClassDetail` / `useClassroomStudents` để lấy dữ liệu học sinh.

---

## 5. Verification Strategy

1. **Backend Verification**:
   - Running `./gradlew compileJava`
   - Unit tests kiểm tra cờ `isOnline` và thứ tự sắp xếp.
2. **Frontend Verification**:
   - Running `npx tsc --noEmit` & `npm run lint`
   - Kiểm tra UI bằng Playwright hoặc trên trình duyệt thực tế.
