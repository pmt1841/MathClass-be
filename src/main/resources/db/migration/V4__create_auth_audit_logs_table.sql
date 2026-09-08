-- Tạo bảng auth_audit_logs lưu nhật ký xác thực người dùng
CREATE TABLE IF NOT EXISTS auth_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_email ON auth_audit_logs(email, created_at DESC);
