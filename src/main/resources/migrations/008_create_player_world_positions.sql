-- Track player positions per world for /world command teleportation
CREATE TABLE player_world_positions (
    player_id UUID NOT NULL,
    world_name VARCHAR(64) NOT NULL,

    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (player_id, world_name)
);

CREATE INDEX idx_player_world_positions_lookup
    ON player_world_positions(player_id, world_name);
