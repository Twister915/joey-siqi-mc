-- Multiple lodestone anchors per region
-- A region is protected if a location is within ANY anchor's radius

CREATE TABLE region_anchors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_id UUID NOT NULL REFERENCES protection_regions(id) ON DELETE CASCADE,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_region_anchors_region ON region_anchors(region_id);

-- Migrate existing single-center data to anchors table
INSERT INTO region_anchors (region_id, x, y, z, created_at)
SELECT id, center_x, center_y, center_z, created_at
FROM protection_regions
WHERE deleted_at IS NULL;

-- Drop the old location index (references columns we're removing)
DROP INDEX idx_protection_regions_location;

-- Drop the old center columns (data now lives in region_anchors)
ALTER TABLE protection_regions DROP COLUMN center_x;
ALTER TABLE protection_regions DROP COLUMN center_y;
ALTER TABLE protection_regions DROP COLUMN center_z;
