-- Cập nhật mặc định tắt dọn dẹp định kỳ cho storage_cleanup_config
ALTER TABLE storage_cleanup_config ALTER COLUMN enabled SET DEFAULT FALSE;

UPDATE storage_cleanup_config SET enabled = FALSE;
