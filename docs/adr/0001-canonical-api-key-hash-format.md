# ADR 0001: Canonical API key hash format

- Status: Accepted

## Context

V15 required `api_key_hash` values to have exactly 64 characters. This was weaker than the guarantee implied by the column name because length alone did not prove that a value was a canonical SHA-256 hash. On MySQL 8.0.36, the schema still accepted 64 non-hexadecimal characters, a leading space followed by 63 hexadecimal characters, and 64 uppercase hexadecimal characters. Values written outside `ApiKeyHasher`, such as direct provisioning statements, could therefore bypass the format produced by the application.

## Decision

The database must accept only the canonical representation produced by `ApiKeyHasher`: exactly 64 lowercase hexadecimal characters. V16 adds the `chk_api_clients_api_key_hash_format` constraint using the case-sensitive regular expression `^[0-9a-f]{64}$`. The migration uses the `c` match flag so that MySQL's case-insensitive collation cannot make uppercase values valid. V15 remains unchanged because an already published and applied Flyway migration must not be edited.

## Alternatives considered

An alternative was to keep only the V15 length constraint and rely on `ApiKeyHasher` to produce valid values. This would avoid duplicating validation between the application and the database, and all current application writes already use the canonical format. It was rejected because the database can also be written through provisioning scripts or other paths that do not pass through the application. Relying only on application code would leave that assumption implicit and unenforced.

## Consequences

The database now rejects non-hexadecimal characters, leading whitespace, and uppercase hexadecimal values regardless of whether a write passes through the application. This also removes a MySQL/H2 divergence: MySQL's collation treated uppercase and lowercase hashes as equal for uniqueness, while H2 treated them as different; uppercase values are now rejected before that difference can matter.

The case-sensitive `c` flag is load-bearing because MySQL's case-insensitive collation would otherwise allow uppercase input. The V15 length constraint remains in the migration history, although the stricter V16 format constraint logically subsumes it and is the constraint MySQL reports for short values. The database and application now duplicate the canonical-format rule, so future changes to the hash representation must update both deliberately.

This migration is only safe because `api_clients` was empty when it was applied. Measured on MySQL 8.0.36: adding the constraint to a table holding a single uppercase hash — a value V15 accepted — fails with error 3819, which would leave Flyway in a failed state and prevent the application from starting. The same precondition as V15's `DROP TABLE` applies here, and the safe window closes as soon as a key is provisioned. Tightening a constraint on a populated table requires backfilling or rewriting the offending rows first; that path was never needed here and was therefore never designed.
