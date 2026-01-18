-- Add context_count column to track how many prior Q&A turns were included as conversation context
ALTER TABLE steve_history ADD COLUMN context_count INTEGER NOT NULL DEFAULT 0;
