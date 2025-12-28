CREATE TABLE player_trails (
    player_id UUID NOT NULL,
    trail_type VARCHAR(32) NOT NULL,
    effect VARCHAR(64) NOT NULL,
    intensity VARCHAR(16) NOT NULL DEFAULT 'medium',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, trail_type)
);
