-- Add week_number to player_progress so stats reset each week
-- Drop the old primary key and add week_number to it

-- First, drop the old table and recreate with week_number
-- (Can't easily alter primary key in PostgreSQL)
DROP TABLE IF EXISTS player_progress;

CREATE TABLE player_progress (
    player_id UUID NOT NULL,
    week_number INTEGER NOT NULL,
    stat_key VARCHAR(128) NOT NULL,
    value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, week_number, stat_key)
);

CREATE INDEX idx_player_progress_week ON player_progress(week_number);

-- Drop the separate weekly table if it was created
DROP TABLE IF EXISTS weekly_player_progress;
