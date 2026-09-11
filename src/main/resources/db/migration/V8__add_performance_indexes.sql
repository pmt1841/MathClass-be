-- ==============================================================================
-- Migration V8: Add Performance Indexes for Core and High-Frequency Tables
-- Platform: MathClass-service (PostgreSQL 16)
-- Task: MAT-367 (Bổ sung index cho các bảng cần thiết để tối ưu hiệu năng)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. SUBMISSION DOMAIN (Bảng có dung lượng lớn & tần suất truy vấn/chấm bài cao nhất)
-- ------------------------------------------------------------------------------
-- Tối ưu tra cứu bài làm của học sinh theo bài tập (findFirstByAssignmentIdAndStudentId, lock row)
CREATE INDEX IF NOT EXISTS idx_submissions_assignment_student 
ON submissions (assignment_id, student_id);

-- Tối ưu giáo viên lọc danh sách bài nộp theo bài tập và trạng thái (findSubmissionsByAssignment, findAllByAssignmentIdAndStatusOrderByUpdatedAtDesc)
CREATE INDEX IF NOT EXISTS idx_submissions_assignment_status_updated 
ON submissions (assignment_id, status, updated_at DESC);

-- Tối ưu học sinh tra cứu danh sách bài nộp / bài đã chấm điểm (findGradedSubmissionsByStudent, countByStudentAndStatus)
CREATE INDEX IF NOT EXISTS idx_submissions_student_status_updated 
ON submissions (student_id, status, updated_at DESC);

-- Tối ưu giáo viên lấy bài chờ chấm sắp xếp theo thời gian nộp (findPendingSubmissionsByTeacher)
CREATE INDEX IF NOT EXISTS idx_submissions_student_status_submitted 
ON submissions (student_id, status, submitted_at DESC);

-- ------------------------------------------------------------------------------
-- 2. ASSIGNMENT DOMAIN (Bảng nghiệp vụ giao bài & phiếu bài tập)
-- ------------------------------------------------------------------------------
-- Tối ưu học sinh/giáo viên lấy danh sách bài tập đã công bố theo lớp (findByClassroom_ClassCodeAndStatus)
CREATE INDEX IF NOT EXISTS idx_assignments_classroom_status 
ON assignments (classroom_id, status);

-- Tối ưu giáo viên quản lý danh sách bài tập do mình tạo (findByTeacherId, countByTeacherIdAndStatus)
CREATE INDEX IF NOT EXISTS idx_assignments_teacher_status 
ON assignments (teacher_id, status);

-- Tối ưu lấy danh sách bài tập thuộc phiếu bài tập (findByAssignmentSheetId)
CREATE INDEX IF NOT EXISTS idx_assignments_sheet_id 
ON assignments (assignment_sheet_id);

-- Tối ưu truy vết bài tập con được phân phối vào các lớp (findByParentId, findByParentIdIn)
CREATE INDEX IF NOT EXISTS idx_assignments_parent_id 
ON assignments (parent_id);

-- Tối ưu scheduled job quét bài tập sắp đến hạn để gửi email thông báo (findByDeadlineBetweenAndIsReminderSentFalseAndStatus)
CREATE INDEX IF NOT EXISTS idx_assignments_reminder 
ON assignments (status, is_reminder_sent, deadline);

-- ------------------------------------------------------------------------------
-- 3. NOTIFICATION DOMAIN (Bảng thông báo tăng trưởng liên tục)
-- ------------------------------------------------------------------------------
-- Tối ưu phân trang xem thông báo người dùng theo thời gian mới nhất (findByUserIdOrderByCreatedAtDesc)
CREATE INDEX IF NOT EXISTS idx_notifications_user_created 
ON notifications (user_id, created_at DESC);

-- Tối ưu đếm thông báo chưa đọc và đánh dấu đã đọc (countByUserIdAndIsReadFalse, markAllAsReadByUserId)
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread 
ON notifications (user_id, is_read);

-- ------------------------------------------------------------------------------
-- 4. CLASSROOM DOMAIN (Lớp học, thành viên & phê duyệt)
-- ------------------------------------------------------------------------------
-- Tối ưu lấy danh sách lớp học do giáo viên phụ trách (findByTeacherId, countByTeacherId)
CREATE INDEX IF NOT EXISTS idx_classrooms_teacher_id 
ON classrooms (teacher_id);

-- Tối ưu tra cứu ngược: tìm danh sách lớp của một học sinh (findByStudentsId, countByStudentsId)
-- (PK classroom_students là (classroom_id, student_id) nên tra cứu theo student_id cần index riêng)
CREATE INDEX IF NOT EXISTS idx_classroom_students_student_id 
ON classroom_students (student_id);

-- Tối ưu giáo viên xem và duyệt yêu cầu tham gia lớp (findByClassroomIdAndStatus)
CREATE INDEX IF NOT EXISTS idx_classroom_join_requests_classroom_status 
ON classroom_join_requests (classroom_id, status);

-- Tối ưu học sinh kiểm tra các yêu cầu vào lớp của mình (findByStudentId)
CREATE INDEX IF NOT EXISTS idx_classroom_join_requests_student_id 
ON classroom_join_requests (student_id);

-- Tối ưu xem nhận xét đánh giá học sinh trong lớp (findByClassCodeAndStudentIdOrderByCreatedAtDesc)
CREATE INDEX IF NOT EXISTS idx_student_remarks_classroom_student_created 
ON student_remarks (classroom_id, student_id, created_at DESC);

-- ------------------------------------------------------------------------------
-- 5. ASSIGNMENT SHEETS & DETAILS (Phiếu bài tập, tags, hình ảnh & đồ thị)
-- ------------------------------------------------------------------------------
-- Tối ưu quản lý danh sách phiếu bài tập theo giáo viên (findByTeacherIdAndTitle, countByTeacherIdAndClassroomIsNull)
CREATE INDEX IF NOT EXISTS idx_assignment_sheets_teacher_id 
ON assignment_sheets (teacher_id);

-- Tối ưu lọc phiếu bài tập theo lớp học
CREATE INDEX IF NOT EXISTS idx_assignment_sheets_classroom_id 
ON assignment_sheets (classroom_id);

-- Tối ưu truy vết phiếu bài tập gốc khi clone / phân phối (findByMasterSheetId)
CREATE INDEX IF NOT EXISTS idx_assignment_sheets_master_sheet_id 
ON assignment_sheets (master_sheet_id);

-- Tối ưu tra cứu ngược bài tập theo tag (uk_assignment_tag là (assignment_id, tag_id) nên cần index riêng cho tag_id)
CREATE INDEX IF NOT EXISTS idx_assignment_tags_tag_id 
ON assignment_tags (tag_id);

-- Tối ưu tải ảnh đính kèm của bài tập
CREATE INDEX IF NOT EXISTS idx_assignment_images_assignment_id 
ON assignment_images (assignment_id);

-- Tối ưu tải dữ liệu vẽ hình JSXGraph của bài tập
CREATE INDEX IF NOT EXISTS idx_assignment_drawings_assignment_id 
ON assignment_drawings (assignment_id);

-- ------------------------------------------------------------------------------
-- 6. AUTH & SECURITY (Phiên đăng nhập & Đặt lại mật khẩu)
-- ------------------------------------------------------------------------------
-- Tối ưu xóa/thu hồi refresh token theo người dùng khi logout (findAllByUserOrderByExpiryDateAsc, deleteByUser)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id 
ON refresh_tokens (user_id);

-- Tối ưu tìm kiếm và vô hiệu hóa token đặt lại mật khẩu của người dùng (findByUserAndIsUsedFalse)
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_used 
ON password_reset_tokens (user_id, is_used);

-- ------------------------------------------------------------------------------
-- 7. SYSTEM LOGS & MONITORING (Nhật ký hệ thống & Báo cáo lỗi)
-- ------------------------------------------------------------------------------
-- Tối ưu phân trang xem log mới nhất và cron job dọn dẹp log định kỳ (deleteByCreatedAtBefore)
CREATE INDEX IF NOT EXISTS idx_system_logs_created_at 
ON system_logs (created_at DESC);

-- Tối ưu lọc log hệ thống theo mức độ và thời gian
CREATE INDEX IF NOT EXISTS idx_system_logs_level_created 
ON system_logs (level, created_at DESC);

-- Tối ưu lọc báo cáo sự cố theo trạng thái và thời gian (findByStatus, findAllByOrderByCreatedAtDesc)
CREATE INDEX IF NOT EXISTS idx_bug_reports_status_created 
ON bug_reports (status, created_at DESC);

-- Tối ưu tải ảnh đính kèm theo báo cáo sự cố
CREATE INDEX IF NOT EXISTS idx_bug_report_images_bug_report_id 
ON bug_report_images (bug_report_id);

-- ------------------------------------------------------------------------------
-- 8. AI CREDIT & API KEYS
-- ------------------------------------------------------------------------------
-- Tối ưu truy vấn đơn hàng nạp credit theo người dùng (findByIdAndUserId)
CREATE INDEX IF NOT EXISTS idx_credit_purchase_orders_user_created 
ON credit_purchase_orders (user_id, created_at DESC);

-- Tối ưu thống kê doanh thu theo gói nạp credit (countPurchasesByPackage)
CREATE INDEX IF NOT EXISTS idx_credit_purchase_orders_package_id 
ON credit_purchase_orders (package_id);

-- Tối ưu chọn API Key ưu tiên cao nhất đang hoạt động theo Provider (findByProviderIdAndStatusOrderByPriorityDesc)
CREATE INDEX IF NOT EXISTS idx_ai_api_keys_provider_status_priority 
ON ai_api_keys (provider_id, status, priority DESC);
