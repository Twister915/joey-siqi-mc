-- Remove numeric ID from homes table and use (player_id, name) as primary key
-- Also normalize all home names to lowercase

-- First, normalize existing home names to lowercase
UPDATE homes SET name = LOWER(TRIM(name));

-- Add owner_id and home_name columns to home_shares
ALTER TABLE home_shares ADD COLUMN owner_id UUID;
ALTER TABLE home_shares ADD COLUMN home_name VARCHAR(64);

-- Populate from existing home_id foreign key
UPDATE home_shares hs
SET owner_id = h.player_id, home_name = h.name
FROM homes h
WHERE hs.home_id = h.id;

-- Drop old constraints and column from home_shares
ALTER TABLE home_shares DROP CONSTRAINT home_shares_home_id_shared_with_id_key;
ALTER TABLE home_shares DROP CONSTRAINT home_shares_home_id_fkey;
ALTER TABLE home_shares DROP COLUMN home_id;
ALTER TABLE home_shares DROP COLUMN id;

-- Make the new columns non-null
ALTER TABLE home_shares ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE home_shares ALTER COLUMN home_name SET NOT NULL;

-- Add composite primary key to home_shares
ALTER TABLE home_shares ADD PRIMARY KEY (owner_id, home_name, shared_with_id);

-- Drop id from homes and make (player_id, name) the primary key
ALTER TABLE homes DROP CONSTRAINT homes_pkey;
ALTER TABLE homes DROP CONSTRAINT homes_player_id_name_key;
ALTER TABLE homes DROP COLUMN id;
ALTER TABLE homes ADD PRIMARY KEY (player_id, name);

-- Add foreign key from home_shares to homes
ALTER TABLE home_shares ADD CONSTRAINT home_shares_home_fkey
    FOREIGN KEY (owner_id, home_name) REFERENCES homes(player_id, name) ON DELETE CASCADE;

-- Recreate index for looking up shares by shared_with_id (was dropped with the table restructure)
DROP INDEX IF EXISTS idx_home_shares_shared_with_id;
CREATE INDEX idx_home_shares_shared_with_id ON home_shares(shared_with_id);
