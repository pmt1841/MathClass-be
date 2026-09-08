-- ==============================================================================
-- Migration V1: Initial Baseline Schema (41 Base Tables)
-- Platform: MathClass-service (PostgreSQL 16)
-- Description: Tạo toàn bộ cấu trúc bảng nền tảng của hệ thống MathClass
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. USER & AUTHENTICATION MODULE
-- ------------------------------------------------------------------------------

-- Bảng người dùng hệ thống (users)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    verification_code VARCHAR(255),
    provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    avatar_url VARCHAR(255),
    date_of_birth DATE,
    gender VARCHAR(20),
    last_active_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Lịch sử mật khẩu người dùng (password_histories)
CREATE TABLE IF NOT EXISTS password_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    hashed_password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_password_history_user_created ON password_histories(user_id, created_at DESC);

-- Danh mục quyền hệ thống (permissions)
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Ánh xạ quyền theo vai trò (role_permissions)
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    CONSTRAINT uk_role_permission UNIQUE (role_name, permission_id)
);

-- Refresh token xác thực JWT (refresh_tokens)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL
);

-- Token đặt lại mật khẩu qua email (password_reset_tokens)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Cấu hình xác thực 2 bước 2FA TOTP (user_two_factor_auth)
CREATE TABLE IF NOT EXISTS user_two_factor_auth (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    secret_key VARCHAR(255),
    temp_secret_key VARCHAR(255),
    enabled_at TIMESTAMP,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Mã dự phòng khôi phục 2FA (user_backup_codes)
CREATE TABLE IF NOT EXISTS user_backup_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    code_hash VARCHAR(100) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_backup_codes_user_id ON user_backup_codes(user_id);

-- ------------------------------------------------------------------------------
-- 2. CLASSROOM DOMAIN
-- ------------------------------------------------------------------------------

-- Lớp học Toán (classrooms)
CREATE TABLE IF NOT EXISTS classrooms (
    id BIGSERIAL PRIMARY KEY,
    class_code VARCHAR(255) NOT NULL UNIQUE,
    class_name VARCHAR(255) NOT NULL,
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    max_students INT,
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Bảng liên kết học sinh trong lớp học (classroom_students)
CREATE TABLE IF NOT EXISTS classroom_students (
    classroom_id BIGINT NOT NULL REFERENCES classrooms(id),
    student_id BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (classroom_id, student_id)
);

-- Yêu cầu tham gia lớp học từ học sinh (classroom_join_requests)
CREATE TABLE IF NOT EXISTS classroom_join_requests (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL REFERENCES classrooms(id),
    student_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Nhận xét, đánh giá định kỳ của giáo viên cho học sinh (student_remarks)
CREATE TABLE IF NOT EXISTS student_remarks (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL REFERENCES classrooms(id),
    student_id BIGINT NOT NULL REFERENCES users(id),
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    strengths TEXT,
    weaknesses TEXT,
    general_assessment TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 3. ASSIGNMENT & DRAWING DOMAIN
-- ------------------------------------------------------------------------------

-- Tập đề / Phiếu bài tập gộp (assignment_sheets)
CREATE TABLE IF NOT EXISTS assignment_sheets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    deadline TIMESTAMP,
    visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    original_author_id BIGINT REFERENCES users(id),
    classroom_id BIGINT REFERENCES classrooms(id),
    master_sheet_id BIGINT REFERENCES assignment_sheets(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Bài tập Toán học (assignments)
CREATE TABLE IF NOT EXISTS assignments (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    deadline TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    original_author_id BIGINT REFERENCES users(id),
    parent_id BIGINT,
    classroom_id BIGINT REFERENCES classrooms(id),
    assignment_sheet_id BIGINT REFERENCES assignment_sheets(id),
    content TEXT,
    max_score DOUBLE PRECISION,
    is_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    allow_resubmit BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Ảnh đính kèm trong đề bài tập (assignment_images)
CREATE TABLE IF NOT EXISTS assignment_images (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT REFERENCES assignments(id),
    image_code VARCHAR(50) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Bản vẽ hình học / Đồ thị JSXGraph trong đề bài (assignment_drawings)
CREATE TABLE IF NOT EXISTS assignment_drawings (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id),
    shape_code VARCHAR(50),
    jsx_graph_data JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Thẻ phân loại bài tập (tags)
CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_tag_type_name UNIQUE (type, name)
);

-- Ánh xạ thẻ cho bài tập (assignment_tags)
CREATE TABLE IF NOT EXISTS assignment_tags (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id),
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_assignment_tag UNIQUE (assignment_id, tag_id)
);

-- ------------------------------------------------------------------------------
-- 4. SUBMISSION DOMAIN
-- ------------------------------------------------------------------------------

-- Bài nộp của học sinh (submissions)
CREATE TABLE IF NOT EXISTS submissions (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    score DOUBLE PRECISION,
    submitted_at TIMESTAMP,
    teacher_feedback TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Lịch sử các phiên bản nộp lại bài tập (submission_versions)
CREATE TABLE IF NOT EXISTS submission_versions (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    content TEXT NOT NULL,
    score DOUBLE PRECISION,
    teacher_feedback TEXT,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_submission_version UNIQUE (submission_id, version_number)
);

-- Bình luận, ghi chú chấm điểm trên bài nộp (submission_comments)
CREATE TABLE IF NOT EXISTS submission_comments (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    quote_text TEXT,
    occurrence_index INT,
    image_code VARCHAR(255),
    content TEXT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sub_comment_sub_ver ON submission_comments(submission_id, version_number);

-- Bản vẽ hình học bài làm của học sinh (submission_drawings)
CREATE TABLE IF NOT EXISTS submission_drawings (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
    shape_code VARCHAR(255) NOT NULL,
    jsx_graph_data JSONB,
    metadata JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Nhật ký gợi ý tư duy bài tập từ AI (submission_hints)
CREATE TABLE IF NOT EXISTS submission_hints (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hint_number INT NOT NULL,
    student_content_snapshot TEXT,
    ai_hint_content TEXT NOT NULL,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_submission_hint_number UNIQUE (submission_id, hint_number)
);
CREATE INDEX IF NOT EXISTS idx_submission_hints_sub_id ON submission_hints(submission_id);

-- ------------------------------------------------------------------------------
-- 5. CHAT DOMAIN (Cấu trúc nền tảng trước V3)
-- ------------------------------------------------------------------------------

-- Tin nhắn trao đổi lớp học (chat_messages)
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_class_student_created ON chat_messages(class_id, student_id, created_at DESC);

-- ------------------------------------------------------------------------------
-- 6. NOTIFICATION DOMAIN
-- ------------------------------------------------------------------------------

-- Thông báo người dùng (notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    link VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Cài đặt tùy chọn nhận thông báo cá nhân (notification_settings)
CREATE TABLE IF NOT EXISTS notification_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    master_email BOOLEAN NOT NULL DEFAULT TRUE,
    teacher_join_request BOOLEAN NOT NULL DEFAULT TRUE,
    teacher_new_submission BOOLEAN NOT NULL DEFAULT TRUE,
    student_new_assignment BOOLEAN NOT NULL DEFAULT TRUE,
    student_graded BOOLEAN NOT NULL DEFAULT TRUE,
    student_deadline_reminder BOOLEAN NOT NULL DEFAULT TRUE
);

-- ------------------------------------------------------------------------------
-- 7. AI SUBSYSTEM & GATEWAY
-- ------------------------------------------------------------------------------

-- Nhà cung cấp dịch vụ AI (ai_providers)
CREATE TABLE IF NOT EXISTS ai_providers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    protocol VARCHAR(30) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    auth_header_name VARCHAR(100),
    auth_header_prefix VARCHAR(50),
    auth_query_param VARCHAR(50),
    health_check_path VARCHAR(255),
    strategy VARCHAR(20) NOT NULL DEFAULT 'PRIORITY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- API Keys kết nối AI Providers (ai_api_keys)
CREATE TABLE IF NOT EXISTS ai_api_keys (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id),
    name VARCHAR(100),
    encrypted_key TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_used TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Cấu hình định tuyến tác vụ AI theo model (ai_task_configs)
CREATE TABLE IF NOT EXISTS ai_task_configs (
    id BIGSERIAL PRIMARY KEY,
    task VARCHAR(50) NOT NULL UNIQUE,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id),
    model VARCHAR(100) NOT NULL,
    temperature NUMERIC(3, 2) NOT NULL DEFAULT 0.7,
    max_token INT NOT NULL DEFAULT 1024,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Quản trị System Prompts (ai_system_prompts)
CREATE TABLE IF NOT EXISTS ai_system_prompts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    task_code VARCHAR(50) NOT NULL,
    default_content TEXT NOT NULL,
    current_content TEXT NOT NULL,
    allowed_variables TEXT NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Lịch sử các phiên bản System Prompt (ai_system_prompt_histories)
CREATE TABLE IF NOT EXISTS ai_system_prompt_histories (
    id BIGSERIAL PRIMARY KEY,
    prompt_id BIGINT NOT NULL REFERENCES ai_system_prompts(id),
    version INT NOT NULL,
    content TEXT NOT NULL,
    change_reason VARCHAR(255),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 8. AI CREDIT & QUOTA SUBSYSTEM
-- ------------------------------------------------------------------------------

-- Tài khoản số dư credit AI của người dùng (user_ai_accounts)
CREATE TABLE IF NOT EXISTS user_ai_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    balance INT NOT NULL DEFAULT 0,
    total_earned INT NOT NULL DEFAULT 0,
    total_spent INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Hạn mức credit mặc định cấp cho tài khoản mới theo vai trò (ai_credit_defaults)
CREATE TABLE IF NOT EXISTS ai_credit_defaults (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(20) NOT NULL UNIQUE,
    default_credits INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Cấu hình phí credit cho từng tác vụ AI (ai_credit_configs)
CREATE TABLE IF NOT EXISTS ai_credit_configs (
    id BIGSERIAL PRIMARY KEY,
    task VARCHAR(50) NOT NULL UNIQUE,
    cost_per_call INT NOT NULL DEFAULT 1,
    tokens_per_credit INT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Danh mục các gói credit người dùng có thể mua (credit_packages)
CREATE TABLE IF NOT EXISTS credit_packages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    credits INT NOT NULL,
    price INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Đơn hàng nạp / mua thêm credit (credit_purchase_orders)
CREATE TABLE IF NOT EXISTS credit_purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    package_id BIGINT NOT NULL REFERENCES credit_packages(id),
    credits INT NOT NULL,
    price INT NOT NULL,
    gateway_code VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(100),
    paid_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Sổ cái lịch sử biến động giao dịch credit (credit_transactions)
CREATE TABLE IF NOT EXISTS credit_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    task VARCHAR(50),
    reference_id BIGINT,
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 9. SYSTEM ADMINISTRATION & STORAGE
-- ------------------------------------------------------------------------------

-- Hệ thống tiếp nhận báo cáo lỗi từ người dùng (bug_reports)
CREATE TABLE IF NOT EXISTS bug_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_email VARCHAR(255) NOT NULL,
    reporter_name VARCHAR(255),
    user_id BIGINT,
    error_type VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Ảnh chụp màn hình đính kèm báo cáo lỗi (bug_report_images)
CREATE TABLE IF NOT EXISTS bug_report_images (
    id BIGSERIAL PRIMARY KEY,
    bug_report_id BIGINT NOT NULL REFERENCES bug_reports(id),
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Nhật ký kiểm toán thao tác quản trị hệ thống (system_logs)
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(255) NOT NULL,
    level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    status VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Cấu hình dọn dẹp file lưu trữ định kỳ (storage_cleanup_config)
CREATE TABLE IF NOT EXISTS storage_cleanup_config (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cron_expression VARCHAR(100) NOT NULL DEFAULT '0 0 3 * * SUN',
    grace_period_hours INT NOT NULL DEFAULT 24,
    last_run_at TIMESTAMP,
    last_run_result_json TEXT,
    updated_at TIMESTAMP
);
