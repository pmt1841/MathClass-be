-- Gỡ bỏ check constraint "tags_type_check" do PostgreSQL / Hibernate sinh ra cho cột type
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_type_check;

-- Gỡ bỏ NOT NULL và constraint cũ nếu có
ALTER TABLE tags ALTER COLUMN type DROP NOT NULL;
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
