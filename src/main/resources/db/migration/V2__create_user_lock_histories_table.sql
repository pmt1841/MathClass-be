-- Bổ sung các cột lưu vết khóa tài khoản vào bảng users
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_reason TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);

-- Tạo bảng user_lock_histories lưu nhật ký khóa / mở khóa tài khoản
CREATE TABLE IF NOT EXISTS user_lock_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    action_type VARCHAR(20) NOT NULL,
    reason TEXT,
    performed_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_lock_histories_user_id ON user_lock_histories(user_id);
