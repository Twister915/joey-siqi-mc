-- Player pets table for pet persistence
CREATE TABLE player_pets (
    player_id UUID PRIMARY KEY,
    pet_type VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'following',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
