-- Private messaging system for player-to-player communication

CREATE TABLE private_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    content TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Find unread messages for a player (join delivery)
CREATE INDEX idx_private_messages_recipient_unread
    ON private_messages(recipient_id, created_at ASC)
    WHERE read_at IS NULL;

-- Count sender's pending messages to a recipient (for max limit)
CREATE INDEX idx_private_messages_sender_recipient_unread
    ON private_messages(sender_id, recipient_id)
    WHERE read_at IS NULL;
