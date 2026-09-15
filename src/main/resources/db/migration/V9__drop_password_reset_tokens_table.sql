-- Xóa bảng password_reset_tokens và các index liên quan (chuyển sang quản lý qua Redis)
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
