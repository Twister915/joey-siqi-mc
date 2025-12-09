-- Player sessions tracking table
-- Tracks player connections for ID lookup, online time calculation, and crash recovery

CREATE TABLE player_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    username VARCHAR(16) NOT NULL,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remote_ip TEXT NOT NULL,
    online_mode BOOLEAN NOT NULL,
    server_session_id UUID NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    disconnected_at TIMESTAMPTZ
);

-- Index for heartbeat UPDATE: WHERE server_session_id = ? AND disconnected_at IS NULL
-- Also supports fixOrphanedSessions (though != can't seek, it still filters)
CREATE INDEX idx_player_sessions_active
    ON player_sessions(server_session_id)
    WHERE disconnected_at IS NULL;

-- Index for disconnect UPDATE: WHERE player_id = ? AND server_session_id = ? AND disconnected_at IS NULL
-- Composite index for precise lookup when recording disconnects
CREATE INDEX idx_player_sessions_player_active
    ON player_sessions(player_id, server_session_id)
    WHERE disconnected_at IS NULL;

-- Index for views and player history queries: ORDER BY player_id, connected_at DESC
-- Supports: player_names (DISTINCT ON), player_name_history (window), player_online_time (GROUP BY),
--           findUsernameById (seek by player_id, get most recent)
CREATE INDEX idx_player_sessions_player_time
    ON player_sessions(player_id, connected_at DESC);

-- Index for username lookups: findPlayerIdByName()
-- Case-insensitive for Minecraft username matching
CREATE INDEX idx_player_sessions_username
    ON player_sessions(LOWER(username), connected_at DESC);

-- View: Current username for each player (most recent session)
CREATE VIEW player_names AS
SELECT DISTINCT ON (player_id)
    player_id,
    online_mode,
    username,
    connected_at AS updated_at
FROM player_sessions
ORDER BY player_id, connected_at DESC;

-- View: All usernames a player has used with contiguous date ranges (handles A->B->A)
-- Uses two levels of subqueries to avoid nested window functions:
-- 1. Inner: compute LAG(username) and LEAD(connected_at)
-- 2. Outer: compute SUM() over the name_changed flag to create groups
CREATE VIEW player_name_history AS
SELECT
    player_id,
    online_mode,
    username,
    MIN(connected_at) AS "from",
    NULLIF(MAX(next_connected), MAX(connected_at)) AS "until"
FROM (
    SELECT
        player_id,
        online_mode,
        username,
        connected_at,
        next_connected,
        SUM(name_changed) OVER (PARTITION BY player_id ORDER BY connected_at) AS name_group
    FROM (
        SELECT
            player_id,
            online_mode,
            username,
            connected_at,
            LEAD(connected_at) OVER w AS next_connected,
            CASE WHEN username != LAG(username) OVER w THEN 1 ELSE 0 END AS name_changed
        FROM player_sessions
        WINDOW w AS (PARTITION BY player_id ORDER BY connected_at)
    ) with_lag
) with_groups
GROUP BY player_id, online_mode, username, name_group;

-- View: Total online time per player
CREATE VIEW player_online_time AS
SELECT
    player_id,
    online_mode,
    SUM(
        COALESCE(disconnected_at, last_seen_at) - connected_at
    ) AS online_time
FROM player_sessions
GROUP BY player_id, online_mode;
