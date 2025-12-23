-- Permission Groups
-- Note: No separate tablist columns - Bukkit teams share prefix/suffix for both tablist and nameplate
CREATE TABLE perm_groups (
    canonical_name VARCHAR(64) PRIMARY KEY,  -- lowercase normalized name
    display_name VARCHAR(64) NOT NULL,       -- case-preserved display name
    priority INTEGER NOT NULL DEFAULT 0,     -- higher = more important in resolution
    is_default BOOLEAN NOT NULL DEFAULT FALSE, -- all players inherit if true
    chat_prefix TEXT,      -- prefix shown in chat messages
    chat_suffix TEXT,      -- suffix shown in chat messages
    nameplate_prefix TEXT, -- prefix shown in tablist AND above player head
    nameplate_suffix TEXT, -- suffix shown in tablist AND above player head
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Group permission grants
-- Each grant specifies a permission, optional world scope, and allow/deny state
CREATE TABLE group_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name VARCHAR(64) NOT NULL REFERENCES perm_groups(canonical_name) ON DELETE CASCADE,
    permission VARCHAR(255) NOT NULL,  -- e.g., "worldedit.wand", "plugin.*"
    world_id UUID,                     -- NULL = global, otherwise world-specific
    state BOOLEAN NOT NULL,            -- TRUE = ALLOW, FALSE = DENY
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_name, permission, world_id)
);

-- Player permission grants
-- Direct permission overrides for individual players
CREATE TABLE player_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    permission VARCHAR(255) NOT NULL,
    world_id UUID,                     -- NULL = global
    state BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(player_id, permission, world_id)
);

-- Player permissible attributes
-- Prefix/suffix overrides for individual players (no tablist - uses nameplate for both)
CREATE TABLE perm_players (
    player_id UUID PRIMARY KEY,
    chat_prefix TEXT,
    chat_suffix TEXT,
    nameplate_prefix TEXT,
    nameplate_suffix TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Player-Group membership
-- Links players to groups (many-to-many)
CREATE TABLE player_groups (
    player_id UUID NOT NULL,
    group_name VARCHAR(64) NOT NULL REFERENCES perm_groups(canonical_name) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, group_name)
);

-- Indexes for efficient queries

-- Find default groups quickly
CREATE INDEX idx_perm_groups_default ON perm_groups(is_default) WHERE is_default = TRUE;

-- Order groups by priority for resolution
CREATE INDEX idx_perm_groups_priority ON perm_groups(priority DESC);

-- Look up permissions by group
CREATE INDEX idx_group_permissions_group ON group_permissions(group_name);

-- Look up permissions by player
CREATE INDEX idx_player_permissions_player ON player_permissions(player_id);

-- Look up groups by player
CREATE INDEX idx_player_groups_player ON player_groups(player_id);

-- Look up players by group (for member listing)
CREATE INDEX idx_player_groups_group ON player_groups(group_name);
