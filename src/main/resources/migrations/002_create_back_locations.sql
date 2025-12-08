-- Stores the most recent back location (death or teleport-from) for /back command
-- Each player has at most one back location
CREATE TABLE back_locations (
    player_id UUID PRIMARY KEY,
    location_type VARCHAR(16) NOT NULL CHECK (location_type IN ('death', 'teleport')),
    world_id UUID NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    pitch REAL NOT NULL,
    yaw REAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
