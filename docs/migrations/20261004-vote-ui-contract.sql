-- MySQL 8.4: apply ONCE to the legacy vote schema with the app stopped.
-- DDL auto-commits; take a backup first. Fresh databases do not need this script.
-- Run before deploying the new app. Do not run against an already upgraded schema.
-- Names longer than 40 characters must be resolved before running (strict mode rejects truncation).
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES');

ALTER TABLE lunch_vote_sessions
    MODIFY COLUMN status ENUM('OPEN', 'CLOSED', 'CONFIRMED') NOT NULL,
    CHANGE COLUMN title name VARCHAR(40) NULL,
    ADD COLUMN closes_at DATETIME(6) NULL;
UPDATE lunch_vote_sessions SET closes_at = COALESCE(closed_at, DATE_ADD(created_at, INTERVAL 3 HOUR));
ALTER TABLE lunch_vote_sessions MODIFY closes_at DATETIME(6) NOT NULL;
CREATE INDEX idx_vote_expiry ON lunch_vote_sessions(status, closes_at);

RENAME TABLE vote_participants TO lunch_participants,
             vote_candidates TO lunch_candidates,
             vote_records TO lunch_ballots,
             confirmed_menus TO lunch_decisions;

ALTER TABLE lunch_participants CHANGE COLUMN lunch_vote_session_id session_id BIGINT NOT NULL;
ALTER TABLE lunch_candidates
    CHANGE COLUMN lunch_vote_session_id session_id BIGINT NOT NULL,
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE lunch_ballots
    CHANGE COLUMN lunch_vote_session_id session_id BIGINT NOT NULL,
    CHANGE COLUMN vote_candidate_id candidate_id BIGINT NOT NULL;

-- Hibernate generated the old two-column UNIQUE name, so locate it by its columns.
SELECT INDEX_NAME INTO @legacy_ballot_unique
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lunch_ballots' AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
GROUP BY INDEX_NAME
HAVING COUNT(*) = 2 AND GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME) = 'session_id,team_member_id';
SET @drop_legacy_unique = CONCAT('ALTER TABLE lunch_ballots DROP INDEX `', @legacy_ballot_unique, '`');
PREPARE migration_statement FROM @drop_legacy_unique;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
ALTER TABLE lunch_ballots ADD CONSTRAINT uk_lunch_ballots UNIQUE(session_id, candidate_id, team_member_id);

ALTER TABLE lunch_decisions
    CHANGE COLUMN lunch_vote_session_id session_id BIGINT NOT NULL,
    ADD COLUMN restaurant_id BIGINT NULL,
    ADD COLUMN confirmation_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    MODIFY confirmed_by_team_member_id BIGINT NULL;
UPDATE lunch_decisions d JOIN lunch_candidates c ON c.id = d.vote_candidate_id SET d.restaurant_id = c.restaurant_id;
ALTER TABLE lunch_decisions MODIFY restaurant_id BIGINT NOT NULL;
-- Legacy mandatory columns would block new scalar-only inserts, so remove them.
ALTER TABLE lunch_decisions DROP COLUMN vote_candidate_id;
ALTER TABLE lunch_candidates DROP COLUMN created_by_team_member_id;
