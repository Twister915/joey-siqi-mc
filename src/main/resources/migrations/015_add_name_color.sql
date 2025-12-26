-- Add name_color column to permissibles for controlling player name color in chat and tablist
ALTER TABLE perm_groups ADD COLUMN name_color VARCHAR(32);
ALTER TABLE perm_players ADD COLUMN name_color VARCHAR(32);
