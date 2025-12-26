-- Tracks players currently in admin creative mode
-- Only stores currently active admin mode sessions (rows deleted on exit)
CREATE TABLE admin_mode_state (
    player_id UUID PRIMARY KEY,
    world_id UUID NOT NULL,
    snapshot_id UUID NOT NULL REFERENCES inventory_snapshots(id) ON DELETE CASCADE,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for snapshot lookups
CREATE INDEX idx_admin_mode_snapshot ON admin_mode_state(snapshot_id);
