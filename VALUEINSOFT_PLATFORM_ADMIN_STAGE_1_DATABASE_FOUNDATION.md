# VALUEINSOFT PLATFORM ADMIN STAGE 1 - DATABASE FOUNDATION

Status:
- Implemented

Date:
- 2026-04-05

Purpose:
- establish the shared database foundation required for a Platform Operations Console
- add lifecycle state, audit, support, and metrics tables before higher-level backend logic

---

## Scope

This stage introduced the first platform-admin database layer through:
- `V19__platform_admin_foundation.sql`
- `V20__platform_admin_capability_seed.sql`

These migrations live in:
- `src/main/resources/db/migration/`

---

## Implemented Migrations

### V19 - Platform Admin Foundation

File:
- `src/main/resources/db/migration/V19__platform_admin_foundation.sql`

Created tables:
- `public.branch_runtime_states`
- `public.tenant_lifecycle_events`
- `public.branch_lifecycle_events`
- `public.platform_admin_audit_log`
- `public.platform_support_notes`
- `public.tenant_daily_metrics`
- `public.branch_daily_metrics`

Key behavior:
- branch runtime state is separated from subscription state
- existing branches are bootstrapped into `branch_runtime_states`
- bootstrap only inserts branches whose `companyId` already exists in `public.tenants`
- new branches are auto-bootstrapped through a trigger only when the tenant exists
- lifecycle events are append-only history tables
- audit logging supports platform-level action traceability
- support notes provide tenant and branch support context
- daily metrics tables prepare for platform analytics and overview widgets

### V20 - Platform Admin Capability Seed

File:
- `src/main/resources/db/migration/V20__platform_admin_capability_seed.sql`

Seeded platform capabilities:
- `platform.admin.write`
- `platform.company.read`
- `platform.company.lifecycle.write`
- `platform.branch.read`
- `platform.branch.lifecycle.write`
- `platform.configuration.read`
- `platform.configuration.write`
- `platform.billing.read`
- `platform.audit.read`
- `platform.support.read`
- `platform.support.write`

Granted to:
- `SupportAdmin`

Module used:
- existing `web_admin`

Scope used:
- `global_admin`

---

## Design Decisions

Existing architecture reused:
- shared `public` configuration and tenant metadata tables
- existing `platform_capabilities` and `role_grants` model
- existing `valueinsoft_set_updated_at()` trigger function

Important decisions:
- lifecycle state was not added directly to `public."Branch"`
- branch runtime control is stored in `public.branch_runtime_states`
- tenant lifecycle history and branch lifecycle history are append-only
- platform audit was introduced before expanding sensitive write operations
- support notes were placed in shared metadata, not tenant schemas

---

## Validation

Migration inventory updated:
- `src/test/java/com/example/valueinsoftbackend/Configuration/FlywayMigrationInventoryTest.java`

Validated through:
- `V20`

---

## Outputs Of This Stage

This stage unlocked:
- branch lock or unlock behavior
- company suspend or resume behavior
- platform audit persistence
- support-note persistence
- future platform analytics read models

This stage did not yet deliver:
- backend endpoints
- services
- controller logic
- UI

Those were handled in later stages.

---

## Next Stage

Next:
- Stage 2 - read-only platform-admin backend APIs
