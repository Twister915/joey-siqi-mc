-- Track the last world a player was in, used to detect stale worlds on join
-- When a player joins and their last world no longer exists, we need to
-- swap their inventory from the old inventory group to the new one

CREATE TABLE player_last_worlds (
    player_id UUID PRIMARY KEY,
    world_uuid UUID NOT NULL,
    inventory_group VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
