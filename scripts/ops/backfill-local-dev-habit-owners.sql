-- One-time local operational backfill.
-- This is intentionally not a Flyway migration.
--
-- Preconditions measured on 2026-09-29:
--   api_clients.id = 1 is Local dev with the expected SHA-256 hash;
--   exactly 29 habits have owner_id IS NULL;
--   their IDs are listed explicitly below.
--
-- The transaction is rolled back if any precondition or postcondition fails.

DELIMITER //

CREATE PROCEDURE backfill_local_dev_habit_owners()
BEGIN
    DECLARE owner_matches int DEFAULT 0;
    DECLARE null_owned_before int DEFAULT 0;
    DECLARE updated_rows int DEFAULT 0;
    DECLARE null_owned_after int DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
BEGIN
ROLLBACK;
RESIGNAL;
END;

START TRANSACTION;

SELECT COUNT(*)
INTO owner_matches
FROM api_clients
WHERE id = 1
  AND name = 'Local dev'
  AND api_key_hash =
      '60a2286a5007c8e4c2664246e14f73936f55b0b96b4652933d90e21b2fa068b8';

IF owner_matches <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'Backfill aborted: Local dev owner mapping does not match';
END IF;

SELECT COUNT(*)
INTO null_owned_before
FROM habits
WHERE owner_id IS NULL;

IF null_owned_before <> 29 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'Backfill aborted: expected exactly 29 null-owned habits';
END IF;

UPDATE habits
SET owner_id = 1
WHERE owner_id IS NULL
  AND id IN (
             1, 2, 3, 4, 5, 6, 7,
             8, 9, 10, 11, 12, 13,
             214, 218, 219, 220, 221,
             222, 223, 224, 225, 226,
             227, 228, 229, 230, 231,
             232
    );

SET updated_rows = ROW_COUNT();

    IF updated_rows <> 29 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'Backfill aborted: update did not affect exactly 29 rows';
END IF;

SELECT COUNT(*)
INTO null_owned_after
FROM habits
WHERE owner_id IS NULL;

IF null_owned_after <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'Backfill aborted: null-owned habits remain';
END IF;

COMMIT;

SELECT
    owner_matches,
    null_owned_before,
    updated_rows,
    null_owned_after;
END//

DELIMITER ;

CALL backfill_local_dev_habit_owners();

DROP PROCEDURE backfill_local_dev_habit_owners;
