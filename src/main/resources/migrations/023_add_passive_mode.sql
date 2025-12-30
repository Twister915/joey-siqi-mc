-- Add passive mode setting to player_settings
ALTER TABLE player_settings ADD COLUMN passive_mode BOOLEAN NOT NULL DEFAULT FALSE;
