-- RS256-signed refresh tokens (~506 chars) exceed the original VARCHAR(500) limit,
-- which was sized for the previous HS256 tokens. Widen to VARCHAR(1024) to leave
-- headroom for future claim additions.
ALTER TABLE active_sessions ALTER COLUMN refresh_token TYPE VARCHAR(1024);
