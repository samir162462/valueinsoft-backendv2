-- =====================================================================
-- P1-4: Eliminate plaintext passwords at rest.
--
-- Historically, users created before bcrypt adoption had their password stored as
-- plaintext, and LegacyAwareBcryptPasswordEncoder compared them directly. That
-- plaintext-compare path has now been removed from the encoder, so any non-bcrypt
-- stored value can no longer authenticate.
--
-- This migration re-hashes any legacy (non-bcrypt) password IN PLACE using bcrypt via
-- pgcrypto, so:
--   * the user's existing password keeps working (now verified as a bcrypt hash),
--   * no plaintext credential remains stored at rest,
--   * the account is flagged password_reset_required = TRUE to force a rotation.
--
-- Postgres gen_salt('bf', 10) produces a standard '$2a$10$...' bcrypt hash that Spring
-- Security's BCryptPasswordEncoder can verify.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Re-hash legacy plaintext passwords in place and force a reset.
UPDATE public.users
SET "userPassword" = crypt("userPassword", gen_salt('bf', 10)),
    password_reset_required = TRUE
WHERE "userPassword" IS NOT NULL
  AND "userPassword" <> ''
  AND "userPassword" NOT LIKE '$2a$%'
  AND "userPassword" NOT LIKE '$2b$%'
  AND "userPassword" NOT LIKE '$2y$%';

-- 2) Accounts with no usable password: force reset (they cannot authenticate until reset).
UPDATE public.users
SET password_reset_required = TRUE
WHERE "userPassword" IS NULL
   OR "userPassword" = '';
