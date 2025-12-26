CREATE TABLE player_resource_packs (
    player_id UUID PRIMARY KEY,
    pack_id VARCHAR(64) NOT NULL,
    set_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
