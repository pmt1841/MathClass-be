-- Gỡ bỏ check constraint "tags_type_check" do PostgreSQL / Hibernate sinh ra cho cột type
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_type_check;

-- Cho phép cột type trong bảng tags nhận giá trị NULL
ALTER TABLE tags ALTER COLUMN type DROP NOT NULL;

-- Gỡ bỏ constraint cũ theo (type, name) nếu có
ALTER TABLE tags DROP CONSTRAINT IF EXISTS uk_tag_type_name;

-- Thêm constraint UNIQUE cho cột name
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_tags_name'
    ) THEN
        ALTER TABLE tags ADD CONSTRAINT uk_tags_name UNIQUE (name);
    END IF;
END $$;

-- Đảm bảo Unique tag ở mức DB không phân biệt hoa/thường (BE-04)
CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_lower_name ON tags (LOWER(TRIM(name)));
