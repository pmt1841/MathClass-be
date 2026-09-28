-- ==============================================================================
-- Migration V13: Tích hợp Cổng Thanh toán VietQR, Webhook SePay & Hoàn tiền Sự cố
-- Nhánh: feature/MAT-361/payment-gateway-integration
-- ==============================================================================

-- 1. Bảng lưu trữ cấu hình cổng thanh toán ngân hàng VietQR & SePay Webhook
CREATE TABLE IF NOT EXISTS payment_configs (
    id BIGSERIAL PRIMARY KEY,
    bank_code VARCHAR(20) NOT NULL DEFAULT 'MB',
    account_number VARCHAR(50) NOT NULL DEFAULT '',
    account_holder_name VARCHAR(100) NOT NULL DEFAULT '',
    sepay_api_key TEXT,
    transfer_syntax_prefix VARCHAR(20) NOT NULL DEFAULT 'MAT',
    qr_template VARCHAR(20) NOT NULL DEFAULT 'compact2',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

-- Khởi tạo bản ghi cấu hình mặc định (chỉ duy nhất 1 bản ghi cấu hình hệ thống)
INSERT INTO payment_configs (bank_code, account_number, account_holder_name, sepay_api_key, transfer_syntax_prefix, qr_template, is_active, created_at, updated_at)
VALUES ('MB', '0348714099', 'MATHCLASS ADMIN', '', 'MAT', 'compact2', true, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- 2. Bổ sung trường order_code ngẫu nhiên cho đơn nạp credit để chống đoán mã và tránh nhầm lẫn giao dịch
ALTER TABLE credit_purchase_orders
    ADD COLUMN IF NOT EXISTS order_code VARCHAR(30);

-- Backfill dữ liệu cho các đơn hàng cũ nếu có
UPDATE credit_purchase_orders
SET order_code = 'ORD' || id
WHERE order_code IS NULL;

ALTER TABLE credit_purchase_orders
    ALTER COLUMN order_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_purchase_orders_code
    ON credit_purchase_orders (order_code);

-- 3. Mở rộng độ dài status trong credit_purchase_orders và bổ sung trường hoàn tiền
ALTER TABLE credit_purchase_orders
    ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE credit_purchase_orders
    ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS refund_bank_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS refund_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS refund_account_name VARCHAR(150);

-- 4. Bổ sung trường order_code và thông tin tài khoản hoàn tiền vào bảng bug_reports
ALTER TABLE bug_reports
    ADD COLUMN IF NOT EXISTS order_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_holder_name VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_bug_reports_order_code
    ON bug_reports (order_code);
