-- Fix UNIQUE constraints that don't handle NULL correctly
-- In PostgreSQL, NULL != NULL, so UNIQUE(group_name, permission, world_id)
-- allows duplicate rows when world_id is NULL.

-- First, clean up any duplicate global permissions (keep the most recent one)
DELETE FROM group_permissions a
USING group_permissions b
WHERE a.id < b.id
  AND a.group_name = b.group_name
  AND a.permission = b.permission
  AND a.world_id IS NULL
  AND b.world_id IS NULL;

DELETE FROM player_permissions a
USING player_permissions b
WHERE a.id < b.id
  AND a.player_id = b.player_id
  AND a.permission = b.permission
  AND a.world_id IS NULL
  AND b.world_id IS NULL;

-- Drop the existing constraints
ALTER TABLE group_permissions DROP CONSTRAINT IF EXISTS group_permissions_group_name_permission_world_id_key;
ALTER TABLE player_permissions DROP CONSTRAINT IF EXISTS player_permissions_player_id_permission_world_id_key;

-- Create partial unique indexes that properly handle NULL world_id
-- For global permissions (world_id IS NULL)
CREATE UNIQUE INDEX idx_group_permissions_global_unique
    ON group_permissions(group_name, permission)
    WHERE world_id IS NULL;

CREATE UNIQUE INDEX idx_player_permissions_global_unique
    ON player_permissions(player_id, permission)
    WHERE world_id IS NULL;

-- For world-specific permissions (world_id IS NOT NULL)
CREATE UNIQUE INDEX idx_group_permissions_world_unique
    ON group_permissions(group_name, permission, world_id)
    WHERE world_id IS NOT NULL;

CREATE UNIQUE INDEX idx_player_permissions_world_unique
    ON player_permissions(player_id, permission, world_id)
    WHERE world_id IS NOT NULL;
