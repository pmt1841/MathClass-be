-- Tăng kích thước cột action trong bảng system_logs để lưu trữ mô tả nhật ký chi tiết và thân thiện
ALTER TABLE system_logs ALTER COLUMN action TYPE VARCHAR(500);
