-- Steve question/answer history
CREATE TABLE steve_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    citations JSONB,
    cost_cents DOUBLE PRECISION,
    model_name VARCHAR(128),
    asked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for player history lookups (most recent first)
CREATE INDEX idx_steve_history_player ON steve_history(player_id, asked_at DESC);
