-- Migration V3: Add recipient_id and chat_type columns to chat_messages table
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS recipient_id BIGINT NULL;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS chat_type VARCHAR(31) NOT NULL DEFAULT 'DIRECT_TEACHER';
ALTER TABLE chat_messages ALTER COLUMN student_id DROP NOT NULL;

-- Composite indexes for optimization
CREATE INDEX IF NOT EXISTS idx_chat_messages_group ON chat_messages(class_id, chat_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_direct ON chat_messages(class_id, sender_id, recipient_id, created_at DESC);

-- Table group_chat_read_states
CREATE TABLE IF NOT EXISTS group_chat_read_states (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_group_chat_read_state UNIQUE (class_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_group_chat_read_state ON group_chat_read_states(class_id, user_id);

