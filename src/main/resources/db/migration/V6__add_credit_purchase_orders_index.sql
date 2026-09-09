-- Composite index tối ưu hóa truy vấn đơn hàng nạp credit theo trạng thái và ngày thanh toán
CREATE INDEX IF NOT EXISTS idx_credit_orders_status_paid_at 
ON credit_purchase_orders (status, paid_at DESC);
