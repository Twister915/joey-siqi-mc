-- Pure inventory snapshot storage (no multi-world knowledge)
-- Just stores player state at a point in time

CREATE TABLE inventory_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,

    -- Raw NBT bytes via Paper's ItemStack.serializeItemsAsBytes()
    inventory_data BYTEA NOT NULL,
    armor_data BYTEA NOT NULL,
    offhand_data BYTEA NOT NULL,
    ender_chest_data BYTEA NOT NULL,

    -- Player state
    xp_level INT NOT NULL,
    xp_progress REAL NOT NULL,
    health DOUBLE PRECISION NOT NULL,
    max_health DOUBLE PRECISION NOT NULL,
    hunger INT NOT NULL,
    saturation REAL NOT NULL,

    -- Potion effects as JSON
    effects_json JSONB,

    -- Arbitrary metadata (caller-defined)
    labels JSONB NOT NULL DEFAULT '{}',

    -- When the snapshot was taken (used for effect decay)
    snapshot_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- For listing player's snapshot history
CREATE INDEX idx_snapshots_player_time
    ON inventory_snapshots(player_id, snapshot_at DESC);

-- For querying by labels (e.g., labels->>'source' = 'periodic')
CREATE INDEX idx_snapshots_labels
    ON inventory_snapshots USING GIN (labels);
