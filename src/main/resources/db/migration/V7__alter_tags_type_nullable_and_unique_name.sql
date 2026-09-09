-- Cho phép cột type trong bảng tags nhận giá trị NULL và bổ sung ràng buộc unique cho cột name
ALTER TABLE tags ALTER COLUMN type DROP NOT NULL;

ALTER TABLE tags DROP CONSTRAINT IF EXISTS uk_tag_type_name;

ALTER TABLE tags ADD CONSTRAINT uk_tags_name UNIQUE (name);
