-- Player merit and level (cumulative, never reset)
CREATE TABLE player_merit (
    player_id UUID PRIMARY KEY,
    total_merit BIGINT NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_player_merit_level ON player_merit(level DESC, total_merit DESC);

-- Cumulative progress stats (lifetime)
CREATE TABLE player_progress (
    player_id UUID NOT NULL,
    stat_key VARCHAR(128) NOT NULL,
    value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, stat_key)
);

-- Weekly challenge progress (resets each week by key)
CREATE TABLE weekly_challenge_progress (
    player_id UUID NOT NULL,
    week_number INTEGER NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (player_id, week_number, challenge_id)
);
CREATE INDEX idx_weekly_progress_week ON weekly_challenge_progress(week_number);

-- Historical completions (for stats/leaderboard)
CREATE TABLE challenge_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    week_number INTEGER NOT NULL,
    merit_earned INTEGER NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_completions_player ON challenge_completions(player_id);
CREATE INDEX idx_completions_week ON challenge_completions(week_number);

-- Weekly online time (for always-active challenge)
CREATE TABLE weekly_online_time (
    player_id UUID NOT NULL,
    week_number INTEGER NOT NULL,
    seconds_online BIGINT NOT NULL DEFAULT 0,
    merit_claimed INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, week_number)
);
