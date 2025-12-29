-- Random teleport cooldowns
CREATE TABLE rtp_cooldowns (
    player_id UUID PRIMARY KEY,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rtp_cooldowns_last_used ON rtp_cooldowns(last_used_at);
