-- MySQL 8.4: repair a schema where ddl-auto=update already created the new tables
-- but the legacy title NOT NULL column remains. Stop the app and take a backup first.
-- This script rejects nonempty legacy child tables rather than silently losing history.
-- New tables and their data remain intact. MySQL DDL auto-commits.
DELIMITER //
CREATE PROCEDURE repair_vote_hybrid_schema()
BEGIN
    DECLARE legacy_rows BIGINT DEFAULT 0;
    DECLARE old_unique VARCHAR(255) DEFAULT NULL;
    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('lunch_participants','lunch_candidates','lunch_ballots','lunch_decisions')) <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'New vote tables missing; use the legacy migration instead';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vote_candidates') THEN
        SELECT COUNT(*) INTO legacy_rows FROM vote_candidates;
        IF legacy_rows > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy candidates require data migration before schema repair'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vote_participants') THEN
        SELECT COUNT(*) INTO legacy_rows FROM vote_participants;
        IF legacy_rows > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy participants require data migration before schema repair'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vote_records') THEN
        SELECT COUNT(*) INTO legacy_rows FROM vote_records;
        IF legacy_rows > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy ballots require data migration before schema repair'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='confirmed_menus') THEN
        SELECT COUNT(*) INTO legacy_rows FROM confirmed_menus;
        IF legacy_rows > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy decisions require data migration before schema repair'; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lunch_vote_sessions' AND COLUMN_NAME='title') THEN
        IF EXISTS (SELECT 1 FROM lunch_vote_sessions WHERE name IS NULL AND CHAR_LENGTH(title)>40) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve legacy titles over 40 characters before repair';
        END IF;
        UPDATE lunch_vote_sessions SET name=title WHERE name IS NULL;
        ALTER TABLE lunch_vote_sessions DROP COLUMN title;
    END IF;
    ALTER TABLE lunch_vote_sessions MODIFY COLUMN status ENUM('OPEN','CLOSED','CONFIRMED') NOT NULL;
    -- Remove an incompatible legacy two-column UNIQUE if one was copied onto the new table.
    SELECT INDEX_NAME INTO old_unique FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lunch_ballots' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'
    GROUP BY INDEX_NAME HAVING COUNT(*)=2 AND GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME)='session_id,team_member_id' LIMIT 1;
    IF old_unique IS NOT NULL THEN
        SET @repair_drop_unique=CONCAT('ALTER TABLE lunch_ballots DROP INDEX `',old_unique,'`');
        PREPARE repair_statement FROM @repair_drop_unique;
        EXECUTE repair_statement;
        DEALLOCATE PREPARE repair_statement;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lunch_ballots' AND INDEX_NAME='uk_lunch_ballots') THEN
        ALTER TABLE lunch_ballots ADD CONSTRAINT uk_lunch_ballots UNIQUE(session_id,candidate_id,team_member_id);
    END IF;
END//
DELIMITER ;
CALL repair_vote_hybrid_schema();
DROP PROCEDURE repair_vote_hybrid_schema;
