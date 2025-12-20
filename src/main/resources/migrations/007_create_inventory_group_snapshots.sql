-- Multi-world specific: maps (player, group) to current snapshot
-- This is a pivot table owned by the multiworld module

CREATE TABLE inventory_group_snapshots (
    player_id UUID NOT NULL,
    inventory_group VARCHAR(64) NOT NULL,
    snapshot_id UUID NOT NULL REFERENCES inventory_snapshots(id),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (player_id, inventory_group)
);

-- For finding a player's snapshot for a specific group
CREATE INDEX idx_group_snapshots_lookup
    ON inventory_group_snapshots(player_id, inventory_group);
