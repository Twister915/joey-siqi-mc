-- Custom whitelist system with invite tracking
CREATE TABLE whitelist (
    player_id UUID PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    invited_by_player_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for audit queries (who did this person invite?)
CREATE INDEX idx_whitelist_invited_by ON whitelist(invited_by_player_id, created_at DESC);
