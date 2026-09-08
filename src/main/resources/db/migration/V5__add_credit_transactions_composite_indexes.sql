-- Composite indexes tối ưu hóa truy vấn lịch sử giao dịch credit theo user_id, type và thời gian
CREATE INDEX IF NOT EXISTS idx_credit_txn_user_created ON credit_transactions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_credit_txn_user_type_created ON credit_transactions (user_id, type, created_at DESC);
