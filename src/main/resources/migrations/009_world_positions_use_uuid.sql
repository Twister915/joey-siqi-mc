-- Change player_world_positions to use world UUID instead of name
-- Drop existing constraints and recreate with world_id

-- Drop existing index
DROP INDEX IF EXISTS idx_player_world_positions_lookup;

-- Recreate table with world_id (simpler than ALTER for primary key change)
DROP TABLE player_world_positions;

CREATE TABLE player_world_positions (
    player_id UUID NOT NULL,
    world_id UUID NOT NULL,

    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (player_id, world_id)
);

CREATE INDEX idx_player_world_positions_lookup
    ON player_world_positions(player_id, world_id);
