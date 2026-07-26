-- =====================================================================
-- Notification Center — Phase 0 database preflight  (NC-0.12)
--
-- Run this against every environment (local, staging, production) BEFORE
-- authoring V168. It is read-only except for the pgcrypto probe, which is
-- rolled back. Nothing here modifies schema.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f notification-phase0-preflight.sql
--
-- Every check prints PASS or FAIL. A FAIL on check 1 blocks V168 entirely:
-- the plan depends on gen_random_uuid() and digest() from pgcrypto.
-- =====================================================================

\set ON_ERROR_STOP off
\pset border 2

\echo '=== 1. PostgreSQL version (need 14+ for the partitioning and index features used) ==='
SELECT
    current_setting('server_version')                       AS server_version,
    CASE WHEN current_setting('server_version_num')::int >= 140000
         THEN 'PASS' ELSE 'FAIL — plan assumes PostgreSQL 14 or later' END AS result;

\echo ''
\echo '=== 2. Is pgcrypto already installed? ==='
SELECT
    COALESCE((SELECT extversion FROM pg_extension WHERE extname = 'pgcrypto'), '(not installed)')
        AS pgcrypto_version,
    CASE WHEN EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto')
         THEN 'PASS — already present, V168 CREATE EXTENSION will be a no-op'
         ELSE 'CHECK 3 REQUIRED — not installed yet' END AS result;

\echo ''
\echo '=== 3. Is pgcrypto AVAILABLE to install? (managed Postgres often allow-lists) ==='
SELECT
    name, default_version,
    CASE WHEN name IS NOT NULL THEN 'PASS — available' ELSE 'FAIL' END AS result
FROM pg_available_extensions
WHERE name = 'pgcrypto';

\echo ''
\echo '=== 4. Can the CURRENT ROLE actually create it? (probe, then rolled back) ==='
\echo '    A permission error here means V168 must be run by a superuser, or the'
\echo '    extension must be pre-created by the platform provider.'
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT
    gen_random_uuid()                                AS uuid_probe,
    encode(digest('valueinsoft', 'sha256'), 'hex')   AS digest_probe,
    'PASS — gen_random_uuid() and digest() both work' AS result;
ROLLBACK;

\echo ''
\echo '=== 5. Current Flyway head (expect 167 before V168 is authored) ==='
SELECT
    MAX(version::numeric)                            AS current_head,
    CASE WHEN MAX(version::numeric) = 167
         THEN 'PASS — head is V167 as the plan assumes'
         ELSE 'REVIEW — head differs from the plan baseline of V167' END AS result
FROM flyway_schema_history
WHERE success AND version IS NOT NULL;

\echo ''
\echo '=== 6. Tenant schemas that V173 will have to bootstrap ==='
SELECT
    COUNT(*)                                         AS tenant_schema_count,
    'INFO — V173 loops over each of these; budget migration time accordingly' AS note
FROM information_schema.schemata
WHERE schema_name ~ '^c_[0-9]+$';

\echo ''
\echo '=== 7. Name collisions — anything already called notification_* ? ==='
SELECT
    COALESCE(string_agg(table_schema || '.' || table_name, ', '), '(none)') AS existing_objects,
    CASE WHEN COUNT(*) = 0
         THEN 'PASS — no collisions'
         ELSE 'REVIEW — rename or reconcile before V168' END AS result
FROM information_schema.tables
WHERE table_name LIKE 'notification%';

\echo ''
\echo '=== 8. Legacy platform outbox that V177 will migrate ==='
SELECT
    COALESCE((SELECT COUNT(*) FROM public.platform_alert_notification_outbox
              WHERE status = 'pending'), 0)          AS pending_rows_to_backfill,
    'INFO — V177 backfills pending rows only' AS note;

\echo ''
\echo '=== 9. Connection-pool posture: is anything keeping the DB awake right now? ==='
SELECT
    state,
    COUNT(*)                                         AS connections,
    MAX(NOW() - state_change)                        AS longest_in_state
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state
ORDER BY connections DESC;

\echo ''
\echo '=== Preflight complete. Every check above must read PASS before V168 is written. ==='
