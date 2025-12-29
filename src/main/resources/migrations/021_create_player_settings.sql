-- Player settings table for per-player preferences
CREATE TABLE player_settings (
    player_id UUID PRIMARY KEY,
    keep_inventory BOOLEAN NOT NULL DEFAULT false,
    display_time VARCHAR(16) NOT NULL DEFAULT 'ALWAYS',
    easy_mode BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for efficient lookups during startup cache loading
CREATE INDEX idx_player_settings_updated ON player_settings(updated_at DESC);
