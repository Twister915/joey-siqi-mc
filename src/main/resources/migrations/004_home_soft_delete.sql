-- Add soft delete support to homes table with UUID-based identity
-- This allows multiple versions of the same (player_id, name) home,
-- with only one active at a time (deleted_at IS NULL).

-- Step 1: Add UUID id column and deleted_at column to homes
ALTER TABLE homes ADD COLUMN id UUID;
ALTER TABLE homes ADD COLUMN deleted_at TIMESTAMPTZ;

-- Step 2: Populate id for existing rows
UPDATE homes SET id = gen_random_uuid() WHERE id IS NULL;

-- Step 3: Make id non-null
ALTER TABLE homes ALTER COLUMN id SET NOT NULL;

-- Step 4: Add home_id column to home_shares (will reference homes.id)
ALTER TABLE home_shares ADD COLUMN home_id UUID;

-- Step 5: Populate home_id from existing owner_id/home_name
UPDATE home_shares hs
SET home_id = h.id
FROM homes h
WHERE hs.owner_id = h.player_id AND hs.home_name = h.name;

-- Step 6: Drop old foreign key constraint
ALTER TABLE home_shares DROP CONSTRAINT home_shares_home_fkey;

-- Step 7: Drop old primary key from homes (must happen before new PK)
ALTER TABLE homes DROP CONSTRAINT homes_pkey;

-- Step 8: Add new primary key on homes.id (must happen before FK reference)
ALTER TABLE homes ADD PRIMARY KEY (id);

-- Step 9: Drop old primary key from home_shares
ALTER TABLE home_shares DROP CONSTRAINT home_shares_pkey;

-- Step 10: Drop old columns from home_shares
ALTER TABLE home_shares DROP COLUMN owner_id;
ALTER TABLE home_shares DROP COLUMN home_name;

-- Step 11: Make home_id non-null
ALTER TABLE home_shares ALTER COLUMN home_id SET NOT NULL;

-- Step 12: Add new primary key to home_shares
ALTER TABLE home_shares ADD PRIMARY KEY (home_id, shared_with_id);

-- Step 13: Add foreign key from home_shares to homes (homes.id is now PK)
ALTER TABLE home_shares ADD CONSTRAINT home_shares_home_fkey
    FOREIGN KEY (home_id) REFERENCES homes(id) ON DELETE CASCADE;

-- Step 14: Create partial unique index for active homes only
-- This ensures only one active home per (player_id, name)
CREATE UNIQUE INDEX idx_homes_active_player_name
    ON homes(player_id, name)
    WHERE deleted_at IS NULL;

-- Step 15: Add index for looking up shares by home_id
CREATE INDEX idx_home_shares_home_id ON home_shares(home_id);
