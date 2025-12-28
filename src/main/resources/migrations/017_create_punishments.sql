-- Punishments system tables for bans, mutes, kicks, and warnings
-- All punishments use soft delete via revoked_at timestamp

-- Create enum type for punishment types
CREATE TYPE punishment_type AS ENUM ('BAN', 'IP_BAN', 'MUTE', 'KICK', 'WARN');

-- Main punishments table (unified for all punishment types)
CREATE TABLE punishments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Target info (player UUID for BAN/MUTE/KICK/WARN, NULL for direct IP bans)
    target_player_id UUID,
    target_ip VARCHAR(45),  -- IPv6 max length

    -- Type discriminator
    type punishment_type NOT NULL,

    -- Who issued it (NULL = console)
    issued_by_player_id UUID,

    -- Details
    reason TEXT,
    expires_at TIMESTAMPTZ,  -- NULL = permanent (for BAN/IP_BAN/MUTE)

    -- Lifecycle
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    revoked_by_player_id UUID,

    -- Constraints: ensure appropriate target is set for each type
    CONSTRAINT chk_target CHECK (
        -- BAN, MUTE, KICK, WARN require target_player_id
        (type IN ('BAN', 'MUTE', 'KICK', 'WARN') AND target_player_id IS NOT NULL)
        OR
        -- IP_BAN requires target_ip (player_id is optional, set if we looked it up from player)
        (type = 'IP_BAN' AND target_ip IS NOT NULL)
    )
);

-- Index for checking active bans for a player at login
CREATE INDEX idx_punishments_player_ban ON punishments(target_player_id, type)
    WHERE type = 'BAN' AND revoked_at IS NULL;

-- Index for checking active IP bans at login
CREATE INDEX idx_punishments_ip_ban ON punishments(target_ip, type)
    WHERE type = 'IP_BAN' AND revoked_at IS NULL;

-- Index for checking active mutes in chat
CREATE INDEX idx_punishments_player_mute ON punishments(target_player_id, type)
    WHERE type = 'MUTE' AND revoked_at IS NULL;

-- Index for punishment history queries (newest first)
CREATE INDEX idx_punishments_history ON punishments(target_player_id, created_at DESC);

-- Index for finding punishments by issuer (for audit purposes)
CREATE INDEX idx_punishments_issuer ON punishments(issued_by_player_id, created_at DESC)
    WHERE issued_by_player_id IS NOT NULL;
