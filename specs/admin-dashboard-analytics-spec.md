# Spec: Tính năng Thống kê Tổng quan Quản trị viên (Admin Dashboard Analytics & KPI Service)

## 1. Objective (Mục tiêu)
Tính năng này cung cấp hệ thống số liệu phân tích và bảng điều khiển tổng quan (Dashboard) cho Quản trị viên (`ADMIN`) trong hệ thống `MathClass-service`.
* **Thống kê chỉ số KPI theo Tháng & Năm**: Tra cứu dữ liệu người dùng (tổng số, giáo viên, học sinh, tài khoản mới trong tháng, DAU), lớp học hoạt động, doanh thu nạp credit, tăng trưởng so với tháng trước và số lượng báo cáo sự cố chờ xử lý.
* **Theo dõi hiệu năng AI Tasks**: Thống kê số lượt gọi, số lượt thành công, số lượt thất bại và tỷ lệ thành công `%` của từng tác vụ AI (`BATCH_QUESTION_GEN`, `QUESTION_GEN`, `SUBMISSION_GRADING`, `STUDENT_HINT`, `STUDENT_REMARK`, `CANVAS_LATEX`).
* **Phân tích gói Credit & Giao dịch**: Thống kê mức độ bán chạy của từng gói nạp credit và danh sách toàn bộ các giao dịch thanh toán thành công trong tháng.
* **Xu hướng biến động theo Năm (Annual Trends)**: Cung cấp xu hướng đăng ký người dùng mới và doanh thu nạp tiền qua 12 tháng của năm đã chọn phục vụ biểu đồ tia mảnh (Sparklines).
* **Widget Xem trước (Preview Tables)**: Trả về 5 bản ghi mới nhất của **Nhật ký hệ thống (`system_logs`)** và 5 bản ghi mới nhất của **Báo cáo sự cố (`bug_reports`)** để hiển thị trực tiếp trên Dashboard kèm liên kết chuyển tiếp chi tiết.

---

## 2. Tech Stack & Environment
* **Language & Framework**: Java 21, Spring Boot 4.1.0, Spring Data JPA, Spring Web, Spring Security.
* **Database**: PostgreSQL 16 (chạy trong Docker container `mathclass-db`).
* **Security**: Spring Security (Kiểm tra quyền quản trị thông qua `@PreAuthorize("hasRole('ADMIN')")`).

---

## 3. Build & Test Commands
```bash
# Biên dịch ứng dụng
./gradlew build -x test

# Chạy Unit Test cho Module Dashboard
./gradlew test --tests "com.codegym.mathclass.dashboard.*"

# Chạy ứng dụng local
./gradlew bootRun

# Khởi chạy môi trường Docker
docker-compose up --build -d
```

---

## 4. Project Structure (Cấu trúc thư mục liên quan)
```
src/main/java/com/codegym/mathclass/
├── dashboard/
│   ├── controller/
│   │   └── AdminDashboardController.java          # [NEW] REST API @RequestMapping("/api/admin/dashboard")
│   ├── dto/admin/
│   │   ├── AdminDashboardStatsResponse.java       # [NEW] DTO tổng hợp toàn bộ số liệu Dashboard
│   │   ├── UserStatsDto.java                      # [NEW] DTO thống kê người dùng
│   │   ├── ClassroomStatsDto.java                 # [NEW] DTO thống kê lớp học
│   │   ├── RevenueStatsDto.java                   # [NEW] DTO thống kê doanh thu & đơn nạp
│   │   ├── BugReportStatsDto.java                 # [NEW] DTO thống kê báo cáo sự cố
│   │   ├── AiTaskUsageDto.java                    # [NEW] DTO thống kê sử dụng tác vụ AI
│   │   ├── PackageSalesDto.java                   # [NEW] DTO thống kê doanh số gói credit
│   │   ├── RecentTransactionDto.java              # [NEW] DTO chi tiết giao dịch nạp credit
│   │   ├── MonthlyUserTrendDto.java               # [NEW] DTO xu hướng người dùng 12 tháng
│   │   ├── MonthlyRevenueTrendDto.java            # [NEW] DTO xu hướng doanh thu 12 tháng
│   │   ├── RecentSystemLogDto.java                # [NEW] DTO 5 nhật ký hệ thống gần nhất
│   │   └── RecentBugReportDto.java                # [NEW] DTO 5 báo cáo sự cố gần nhất
│   └── service/
│       ├── AdminDashboardService.java             # [NEW] Interface tính toán thống kê KPI
│       └── impl/
│           └── AdminDashboardServiceImpl.java     # [NEW] Implementation truy vấn dữ liệu theo tháng/năm
├── aiconfig/credit/repository/
│   ├── CreditPurchaseOrderRepository.java         # [MODIFY] Thêm query doanh thu tháng & xu hướng năm
│   └── CreditTransactionRepository.java           # [MODIFY] Thêm query đếm tác vụ AI theo loại
├── user/repository/
│   └── UserRepository.java                        # [MODIFY] Thêm query đếm user theo mốc thời gian & theo tháng
├── bugreport/repository/
│   └── BugReportRepository.java                   # [MODIFY] Thêm findTop5ByOrderByCreatedAtDesc()
└── systemlog/repository/
    └── SystemLogRepository.java                   # [MODIFY] Thêm findTop5ByOrderByCreatedAtDesc()
```

---

## 5. Detailed Specifications & Schemas

### 5.1 Data Model & Repositories

#### A. Truy vấn Thống kê Doanh thu & Gói Nạp (`CreditPurchaseOrderRepository`)
* `sumPriceByStatusAndPaidAtBetween(CreditPurchaseOrderStatus status, LocalDateTime start, LocalDateTime end)`: Tính tổng doanh thu nạp thành công trong khoảng thời gian.
* `countByStatusAndPaidAtBetween(CreditPurchaseOrderStatus status, LocalDateTime start, LocalDateTime end)`: Đếm số lượng đơn nạp thành công.
* `countPurchasesByPackageAndPaidAtBetween(CreditPurchaseOrderStatus status, LocalDateTime start, LocalDateTime end)`: Đếm số lượt mua theo từng `packageId`.
* `findByStatusAndPaidAtBetweenOrderByPaidAtDesc(...)`: Lấy danh sách giao dịch nạp thành công trong tháng.
* `sumRevenueByMonthOfYear(LocalDateTime startOfYear, LocalDateTime endOfYear)`: Gom nhóm tổng doanh thu theo từng tháng (1 -> 12) của năm.

#### B. Truy vấn Thống kê Tác vụ AI (`CreditTransactionRepository`)
* `countTaskTransactionsByTypesAndCreatedAtBetween(List<CreditTransactionType> types, LocalDateTime start, LocalDateTime end)`: Gom nhóm theo `task` và `type` (`CONSUME` tính là thành công, `REFUND` tính là thất bại hoàn tiền).

#### C. Truy vấn Thống kê Người dùng (`UserRepository`)
* `countByCreatedAtLessThan(LocalDateTime endDate)`: Tổng user tích lũy đến hết tháng.
* `countByRoleAndCreatedAtLessThan(Role role, LocalDateTime endDate)`: Tổng user theo Role tích lũy đến hết tháng.
* `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end)`: User đăng ký mới trong tháng.
* `countByLastActiveAtGreaterThanEqual(LocalDateTime todayStart)`: DAU hôm nay (chỉ tính khi xem tháng hiện tại).
* `countNewUsersByMonthOfYear(LocalDateTime startOfYear, LocalDateTime endOfYear)`: Gom nhóm số user đăng ký mới theo 12 tháng trong năm.

#### D. Truy vấn Xem trước Nhật ký & Sự cố
* `SystemLogRepository.findTop5ByOrderByCreatedAtDesc()`: Lấy 5 bản ghi nhật ký mới nhất.
* `BugReportRepository.findTop5ByOrderByCreatedAtDesc()`: Lấy 5 bản ghi báo cáo sự cố mới nhất.

---

### 5.2 API Specifications & Authorization

#### `GET /api/admin/dashboard/stats`
* **Endpoint**: `GET /api/admin/dashboard/stats`
* **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
* **Query Parameters**:
  * `month` (Integer, optional, 1 - 12): Tháng cần tra cứu. Mặc định là tháng hiện tại.
  * `year` (Integer, optional, 2000 - 2100): Năm cần tra cứu. Mặc định là năm hiện tại.
* **Logic Xử Lý**:
  1. Xác định mốc thời gian: `startDate = 01/{month}/{year} 00:00:00`, `endDate = 01/{month+1}/{year} 00:00:00`, `startOfLastMonth = 01/{month-1}/{year} 00:00:00`.
  2. Tính chỉ số người dùng: Tổng người dùng, số giáo viên, số học sinh tích lũy đến `endDate`. Số người dùng mới trong khoảng `[startDate, endDate)`. Tính DAU hôm nay nếu là tháng hiện tại (nếu tháng quá khứ gán 0).
  3. Tính số lớp học tích lũy đến `endDate`.
  4. Tính doanh thu tháng: `SUM(price)` của các đơn `SUCCESS` trong `[startDate, endDate)`. So sánh với `[startOfLastMonth, startDate)` để tính `%` tăng trưởng.
  5. Tính số bug report pending tích lũy đến `endDate`.
  6. Tính tỷ lệ sử dụng 6 tác vụ AI: `BATCH_QUESTION_GEN`, `QUESTION_GEN`, `SUBMISSION_GRADING`, `STUDENT_HINT`, `STUDENT_REMARK`, `CANVAS_LATEX` kèm số lượt thành công, số lượt thất bại và `successRate`.
  7. Lấy danh sách phân bổ gói credit bán chạy và toàn bộ đơn nạp tiền thành công trong tháng.
  8. Lấy xu hướng 12 tháng (Tháng 1 -> 12) của năm `year` cho Đăng ký người dùng và Doanh thu.
  9. Lấy 5 bản ghi mới nhất của `SystemLog` và 5 bản ghi mới nhất của `BugReport`.
* **Response Schema (200 OK)**:
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "selectedMonth": 9,
    "selectedYear": 2026,
    "userStats": {
      "totalUsers": 1240,
      "teacherCount": 180,
      "studentCount": 1060,
      "newUsersThisWeek": 34,
      "activeUsersToday": 125
    },
    "classroomStats": {
      "activeClassesCount": 48
    },
    "revenueStats": {
      "monthlyRevenue": 14850000,
      "growthPercentage": 14.2,
      "successfulOrdersCount": 82
    },
    "bugReportStats": {
      "pendingCount": 6
    },
    "aiTaskUsages": [
      {
        "taskCode": "BATCH_QUESTION_GEN",
        "taskName": "AI Tách đề thi",
        "callCount": 450,
        "successCount": 440,
        "failedCount": 10,
        "successRate": 97.8,
        "percentage": 30.0
      },
      {
        "taskCode": "CANVAS_LATEX",
        "taskName": "AI Nhận diện hình ảnh & viết tay",
        "callCount": 80,
        "successCount": 75,
        "failedCount": 5,
        "successRate": 93.8,
        "percentage": 5.4
      }
    ],
    "packageSales": [
      {
        "packageId": 1,
        "packageName": "Gói Khởi Động",
        "credits": 100,
        "price": 20000,
        "salesCount": 45
      }
    ],
    "recentTransactions": [
      {
        "orderId": 101,
        "userId": 12,
        "fullName": "Nguyễn Văn A",
        "avatarUrl": "https://...",
        "role": "TEACHER",
        "packageName": "Gói Tiêu Chuẩn",
        "price": 100000,
        "credits": 600,
        "status": "SUCCESS",
        "paidAt": "2026-09-08T15:30:00"
      }
    ],
    "userTrends": [
      { "month": 1, "count": 20 },
      { "month": 9, "count": 85 },
      { "month": 12, "count": 0 }
    ],
    "revenueTrends": [
      { "month": 1, "revenue": 1200000 },
      { "month": 9, "revenue": 7200000 },
      { "month": 12, "revenue": 0 }
    ],
    "recentSystemLogs": [
      {
        "id": 101,
        "actor": "admin@mathclass.edu.vn",
        "resourceType": "USER",
        "action": "Cập nhật phân quyền giảng viên",
        "level": "INFO",
        "createdAt": "2026-09-09T10:00:00"
      }
    ],
    "recentBugReports": [
      {
        "id": 51,
        "reporterEmail": "student@mathclass.edu.vn",
        "errorType": "SUBMISSION_PROBLEM",
        "description": "Lỗi không hiển thị đề kiểm tra",
        "status": "PENDING",
        "createdAt": "2026-09-09T11:00:00"
      }
    ]
  }
}
```

---

## 6. Boundaries (Ranh giới & Quy tắc)

* **Luôn làm (`Always do`)**:
  * Sử dụng đúng chuẩn đường dẫn `/api/...` (không dùng `/api/v1/...`).
  * Sử dụng `@PreAuthorize("hasRole('ADMIN')")` bảo vệ endpoint quản trị.
  * Lọc dữ liệu lũy kế tính đến hết tháng được chọn (`createdAt < endDate`) cho Tổng người dùng, Giáo viên, Học sinh, Lớp học, Bug pending để đảm bảo độ chính xác khi xem quá khứ.
  * Gán `activeUsersToday = 0` khi tra cứu tháng quá khứ (vì DAU chỉ có ý nghĩa đối với ngày hôm nay).
  * Chuẩn hóa tên hiển thị tác vụ `CANVAS_LATEX` là `"AI Nhận diện hình ảnh & viết tay"`.
  * Trả về đủ 12 tháng cho `userTrends` và `revenueTrends` (nếu tháng chưa có dữ liệu thì giá trị mặc định là 0).
  * Trả về top 5 bản ghi mới nhất cho nhật ký hệ thống và báo cáo sự cố.
* **Cần xác nhận trước (`Ask first`)**:
  * Thay đổi công thức tính tỷ lệ tăng trưởng doanh thu so với tháng trước.
* **Không bao giờ làm (`Never do`)**:
  * Thêm prefix `/v1` vào endpoint API.
  * Cho phép người dùng không có role `ADMIN` truy cập dữ liệu thống kê quản trị.
  * Trả về thực thể JPA Entity trực tiếp ra API Response (luôn đóng gói qua DTO).

---

## 7. Success Criteria (Tiêu chí Nghiệm thu)

1. [ ] Endpoint `GET /api/admin/dashboard/stats` tuân thủ đúng định dạng đường dẫn `/api/...`.
2. [ ] Bảo mật phân quyền chính xác: Chỉ tài khoản có Role `ADMIN` mới được phép gọi API.
3. [ ] Hỗ trợ truyền tham số `month` và `year` linh hoạt; nếu không truyền thì tự động nhận diện tháng và năm hiện tại.
4. [ ] Số liệu người dùng, giáo viên, học sinh, lớp học phản ánh chính xác mốc lũy kế đến hết tháng được chọn.
5. [ ] Số liệu tài khoản mới và doanh thu tính chính xác trong phạm vi của tháng được chọn.
6. [ ] Thống kê phân bổ tác vụ AI cung cấp đầy đủ: số lượt gọi, lượt thành công, lượt hoàn tiền (thất bại) và tỷ lệ thành công `%`.
7. [ ] Cung cấp đủ 12 tháng cho biểu đồ xu hướng người dùng và xu hướng doanh thu của năm đã chọn.
8. [ ] Trả về 5 bản ghi mới nhất của `recentSystemLogs` với các trường `actor`, `resourceType`, `action`, `level`, `createdAt`.
9. [ ] Trả về 5 bản ghi mới nhất của `recentBugReports` với các trường `reporterEmail`, `errorType`, `description`, `status`, `createdAt`.
10. [ ] Unit Tests module Dashboard biên dịch và kiểm thử thành công 100% qua `./gradlew test`.

---

## 8. Comprehensive Test Cases Matrix (Danh sách Test Cases kiểm thử)

| Mã TC | Phân loại | Tên Test Case | Điều kiện đầu vào / Bước thực hiện | Kết quả mong đợi (Expected Outcome) |
| :--- | :--- | :--- | :--- | :--- |
| **TC01** | **Authorization** | Truy cập API với vai trò `ADMIN` | Gửi `GET /api/admin/dashboard/stats` kèm JWT token có Role `ADMIN`. | Phản hồi `200 OK`, trả về đầy đủ đối tượng `AdminDashboardStatsResponse`. |
| **TC02** | **Authorization** | Từ chối truy cập đối với `TEACHER` hoặc `STUDENT` | Gửi `GET /api/admin/dashboard/stats` với token của Giáo viên hoặc Học sinh. | Phản hồi `403 Forbidden`. |
| **TC03** | **Authorization** | Từ chối truy cập khi chưa xác thực (Anonymous) | Gửi `GET /api/admin/dashboard/stats` không kèm header Authorization. | Phản hồi `401 Unauthorized`. |
| **TC04** | **Default Filter** | Lấy dữ liệu thống kê tháng hiện tại (Mặc định) | Gọi `GET /api/admin/dashboard/stats` không truyền query params `month` và `year`. | Hệ thống tự động nhận diện tháng/năm hiện tại (`selectedMonth = now.month`, `selectedYear = now.year`), tính DAU hôm nay > 0 nếu có user hoạt động. |
| **TC05** | **Time Travel Filter** | Tra cứu số liệu tháng trong quá khứ | Gọi `GET /api/admin/dashboard/stats?month=1&year=2024`. | Trả về số liệu lũy kế tính đến hết 31/01/2024. Trường `activeUsersToday` trả về `0`. |
| **TC06** | **KPI Calculation** | Kiểm tra tính toán tăng trưởng doanh thu tháng | Tháng 9 đạt 5.000.000 VNĐ, tháng 8 đạt 4.000.000 VNĐ. | `monthlyRevenue = 5000000`, `growthPercentage = 25.0`. |
| **TC07** | **AI Task SLA** | Thống kê tác vụ AI tính cả thành công và thất bại | Tác vụ `BATCH_QUESTION_GEN` có 58 lượt `CONSUME` và 2 lượt `REFUND`. | `callCount = 60`, `successCount = 58`, `failedCount = 2`, `successRate = 96.7%`. |
| **TC08** | **AI Task Naming** | Kiểm tra nhãn tác vụ `CANVAS_LATEX` | Dữ liệu trả về cho task code `CANVAS_LATEX`. | `taskName` hiển thị đúng là `"AI Nhận diện hình ảnh & viết tay"`. |
| **TC09** | **Annual Trends** | Cung cấp đủ 12 tháng trong năm cho User & Revenue Trends | Gọi thống kê cho năm 2026. | `userTrends` và `revenueTrends` đều có độ dài đúng 12 phần tử đại diện từ Tháng 1 đến Tháng 12. |
| **TC10** | **Recent Logs Preview** | Lấy 5 bản ghi nhật ký hệ thống mới nhất | Trong DB có nhiều hơn 5 bản ghi `system_logs`. | `recentSystemLogs` trả về đúng 5 bản ghi mới nhất sắp xếp giảm dần theo `createdAt`. |
| **TC11** | **Recent Bugs Preview** | Lấy 5 bản ghi báo cáo sự cố mới nhất | Trong DB có nhiều hơn 5 bản ghi `bug_reports`. | `recentBugReports` trả về đúng 5 bản ghi mới nhất sắp xếp giảm dần theo `createdAt`. |
| **TC12** | **Empty Data Handling** | Xử lý khi cơ sở dữ liệu chưa có giao dịch hoặc log | Gọi API trong hệ thống mới khởi tạo hoặc tháng không có phát sinh dữ liệu. | Phản hồi `200 OK`, các danh sách trả về mảng rỗng `[]`, các số đếm/doanh thu trả về `0`, không gây lỗi NullPointerException. |
