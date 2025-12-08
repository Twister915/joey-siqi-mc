CREATE TABLE homes (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    world_id UUID NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    pitch REAL NOT NULL,
    yaw REAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(player_id, name)
);

CREATE TABLE home_shares (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    shared_with_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(home_id, shared_with_id)
);

CREATE INDEX idx_homes_player_id ON homes(player_id);
CREATE INDEX idx_home_shares_shared_with_id ON home_shares(shared_with_id);
