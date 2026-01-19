-- Add PvP access level to protection regions
ALTER TABLE protection_regions
ADD COLUMN pvp_access VARCHAR(16) NOT NULL DEFAULT 'OWNER';
