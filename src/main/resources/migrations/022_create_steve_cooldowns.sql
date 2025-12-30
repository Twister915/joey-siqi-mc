-- Steve AI chatbot cooldowns
CREATE TABLE steve_cooldowns (
    player_id UUID PRIMARY KEY,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_steve_cooldowns_last_used ON steve_cooldowns(last_used_at);
