-- Protection regions for lodestone-based land protection

CREATE TABLE protection_regions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    world_id UUID NOT NULL,
    center_x INTEGER NOT NULL,
    center_y INTEGER NOT NULL,
    center_z INTEGER NOT NULL,
    radius INTEGER NOT NULL DEFAULT 16,
    -- Access control levels: 'OWNER', 'MEMBERS', 'EVERYBODY'
    building_access VARCHAR(16) NOT NULL DEFAULT 'MEMBERS',
    container_access VARCHAR(16) NOT NULL DEFAULT 'MEMBERS',
    door_access VARCHAR(16) NOT NULL DEFAULT 'EVERYBODY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE region_members (
    region_id UUID NOT NULL REFERENCES protection_regions(id) ON DELETE CASCADE,
    member_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (region_id, member_id)
);

-- Indexes
CREATE INDEX idx_protection_regions_owner ON protection_regions(owner_id);
CREATE INDEX idx_protection_regions_world ON protection_regions(world_id);
CREATE INDEX idx_protection_regions_location ON protection_regions(world_id, center_x, center_z);
CREATE UNIQUE INDEX idx_protection_regions_active_name
    ON protection_regions(owner_id, LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_region_members_player ON region_members(member_id);
