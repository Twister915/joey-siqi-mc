-- Anti-cheat violation tracking
CREATE TABLE cheat_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    server_session_id UUID NOT NULL,
    check_name VARCHAR(64) NOT NULL,
    violation_weight DOUBLE PRECISION NOT NULL,
    violation_level DOUBLE PRECISION NOT NULL,

    -- Evidence
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    player_location JSONB NOT NULL,
    violation_data JSONB,

    -- Moderation
    reviewed_at TIMESTAMPTZ,
    reviewed_by_player_id UUID,
    verdict VARCHAR(32),
    notes TEXT
);

CREATE INDEX idx_violations_player_time ON cheat_violations(player_id, detected_at DESC);
CREATE INDEX idx_violations_unreviewed ON cheat_violations(detected_at ASC) WHERE reviewed_at IS NULL;
CREATE INDEX idx_violations_session ON cheat_violations(server_session_id, detected_at DESC);
