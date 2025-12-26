-- Nickname storage table
-- Stores custom display names for players

CREATE TABLE player_nicknames (
    player_id UUID PRIMARY KEY,
    nickname VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique index on lowercase nickname for case-insensitive uniqueness
CREATE UNIQUE INDEX idx_player_nicknames_unique
    ON player_nicknames(LOWER(nickname));

-- Index for nickname lookups (resolving nickname to player_id)
CREATE INDEX idx_player_nicknames_lookup
    ON player_nicknames(LOWER(nickname));

-- View showing player display information
-- Combines usernames with nicknames for easy display name lookup
CREATE VIEW player_display_names AS
SELECT
    pn.player_id,
    COALESCE(nick.nickname, pn.username) AS display_name,
    pn.username,
    nick.nickname,
    GREATEST(pn.updated_at, nick.updated_at) AS updated_at
FROM player_names pn
LEFT JOIN player_nicknames nick ON pn.player_id = nick.player_id;
