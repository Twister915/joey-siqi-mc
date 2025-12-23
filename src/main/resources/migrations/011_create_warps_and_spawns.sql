-- Warp locations (server-wide teleport points)
CREATE TABLE warps (
    name VARCHAR(64) PRIMARY KEY,
    world_id UUID NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL DEFAULT 0,
    pitch REAL NOT NULL DEFAULT 0,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Spawn locations per world (only one per world)
CREATE TABLE world_spawns (
    world_id UUID PRIMARY KEY,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL DEFAULT 0,
    pitch REAL NOT NULL DEFAULT 0,
    set_by UUID,
    set_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_warps_created_at ON warps(created_at DESC);
