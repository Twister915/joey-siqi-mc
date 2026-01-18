-- Add source column to distinguish detection origins (custom vs grim)
ALTER TABLE cheat_violations
ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'custom';

CREATE INDEX idx_violations_source ON cheat_violations(source);
