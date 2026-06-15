# Backend Best-Practice Investigation Report

Investigation date: 2026-06-15

Scope: current local state of `C:\Web\Backend VLS\valueinsoft-backendv2`. The worktree already contains many modified and renamed files; this report treats the current local state as the source of truth. No application code, migrations, or configuration files were changed for this investigation.

## 1. Executive Summary

Overall backend health rating: **6 / 10**

The backend has several strong production-readiness investments: Spring Boot 3.4.5 on Java 21, capability-based authorization in many newer controllers, centralized tenant SQL identifier generation, Flyway migration history, broad unit test coverage for newer modules, AI SQL validation, AI audit/cost services, and structured domain services for finance, billing, offline POS sync, dynamic pricing, payroll, and AI/RAG.

The main risk is uneven maturity. Newer modules are much stronger than legacy modules, but the codebase still has public company/payment endpoints, committed secret defaults, manual authorization checks, dynamic per-tenant/per-branch table creation, large database gateway classes, very verbose logging defaults, Flyway disabled by default in some profiles, and a few endpoints/services that expose sensitive data or need stronger validation.

Top 5 urgent risks:

1. **Committed secrets and secret defaults** in `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`, and `config/application-dev.local.properties`.
2. **Public security allowlist includes company read/create and payment checkout endpoints** in `SecurityPack/SecurityConfiguration.java:58-67`; `CompanyController.java:39-86` exposes tenant metadata and signup creation without auth.
3. **Authorization is manual and duplicated**, so coverage depends on each controller/service remembering to call `AuthorizationService`; there is no annotation/aspect-level guard.
4. **Dynamic tenant SQL is widespread**, and while `TenantSqlIdentifiers` is a good hardening point, branch table provisioning and destructive branch deletion still rely on runtime DDL in `DatabaseRequests/DbBranch.java:119-274`.
5. **Logging can expose sensitive data**, especially `/Company/listHeaders` logging all headers in `CompanyController.java:104-110`, plus DEBUG/trace defaults in `application.properties:132-137`.

Top 5 opportunities:

1. Centralize endpoint authorization with annotations or route metadata backed by `AuthorizationService`.
2. Remove secrets from source, add startup-fail validation for required prod secrets, and rotate exposed credentials.
3. Gradually replace per-branch physical POS tables with shared tenant tables keyed by `company_id` and `branch_id`, starting with new modules.
4. Split very large services/repositories into focused query, command, validator, and mapper components.
5. Add integration tests for tenant isolation, authorization, payment callbacks, dynamic pricing, inventory stock movement, and AI/RAG scoping.

## 2. Current Architecture Overview

Observed structure:

- `Controller`: REST endpoints, mixed legacy and newer style.
- `Service`: business logic, with some newer domain subpackages such as `finance`, `billing`, `branch`, `company`, `inventory`, `payment`, `payroll`, `platform`, `product`, and `security`.
- `DatabaseRequests`: JDBC-based persistence gateways, including many legacy classes and newer read/write model gateways.
- `Model`: request, response, entity-like, and domain objects.
- `SecurityPack`: Spring Security configuration, user details service, password encoder.
- `Config`: application configuration and property binding.
- `ExceptionPack`: global API error handling.
- `ai`, `pricing`, `customerbehavior`, `pos/offline`, `fx`, `whatsapp`: more module-oriented packages.
- `src/main/resources/db/migration`: Flyway migrations, currently 108 SQL migration files.

Key metrics from inspection:

- Main Java files: 955.
- Test Java files: 72.
- SQL migrations: 108.
- REST controllers: 57.
- Services: 184.
- Repository/component persistence-style classes: 134.
- JDBC query/update/execute call sites: about 737.

Important classes discovered:

- `util/TenantSqlIdentifiers.java`: central tenant schema/table identifier builder.
- `SecurityPack/SecurityConfiguration.java`: global security chain and public allowlist.
- `Service/security/AuthorizationService.java`: capability checks.
- `Service/security/AuthenticatedEffectiveConfigurationService.java`: resolves authenticated tenant/branch context.
- `Service/platform/PlatformAuthorizationService.java`: global admin capability checks.
- `ExceptionPack/GlobalExceptionHandler.java`: consistent error envelope.
- `DatabaseRequests/DbBranch.java`: branch creation/deletion and legacy per-branch table DDL.
- `ai/sql/AiSqlValidator.java` and `ai/sql/AiSqlExecutor.java`: AI SQL guardrails.
- `ai/service/AiPermissionService.java` and `ai/service/AiSecurityContextResolver.java`: AI tenant/branch authorization.
- `ai/service/AiToolRegistry.java`: tool registry and assistant behaviors.
- `ai/tools/CustomerAiRepository.java`: AI customer data reads.

Main backend patterns:

- Spring MVC REST controllers with manual `AuthorizationService` calls.
- JDBC through `JdbcTemplate` and `NamedParameterJdbcTemplate`; no JPA found in the inspected files.
- Tenant isolation through public tables plus tenant schemas named `c_<companyId>`.
- Some legacy physical branch tables such as `PosOrder_<branchId>`, `PosProduct_<branchId>`, and `InventoryTransactions_<branchId>`.
- Flyway migrations exist, but runtime schema compatibility initializers and disabled Flyway defaults remain.
- AI features use both deterministic tools and an AI SQL agent with validation.

## 3. Critical Findings

### Finding 1 - Secrets are committed or have dangerous source defaults

- Severity: Critical
- Area: Security / configuration
- Files:
  - `src/main/resources/application.properties:98-109`
  - `src/main/resources/application-dev.properties:9,49`
  - `config/application-dev.local.properties:9-24`
- What is wrong: PayMob, S3, Google AI, OpenAI, DeepSeek, JWT, and database-related values are present as defaults or local config values in source-controlled paths. This report intentionally does not repeat the secret values.
- Why it matters: Any committed secret should be considered compromised. Defaults can also accidentally activate in dev, CI, or non-prod environments and leak into logs, deployments, or screenshots.
- Recommended fix: Rotate exposed credentials, remove secret values from repo files, keep only empty placeholders, add `.gitignore` coverage for local config, and add startup validation that fails production boot if required secrets are missing.
- Implementation complexity: Medium
- Suggested priority: Phase 0

### Finding 2 - Public endpoint allowlist is too broad

- Severity: Critical
- Area: Security / API exposure
- Files:
  - `SecurityPack/SecurityConfiguration.java:58-67`
  - `Controller/CompanyController.java:39-86`
  - `OnlinePayment/OPController/PayMobController.java:53-72`
- What is wrong: `/Company/getCompany`, `/Company/getCompanyById`, `/Company/saveCompany`, and `/OP/TPC` are explicitly public. `/OP/paymentTKNRequest` is not in the public allowlist, but it has no `Principal`, no `@Valid`, and no capability check. If reached by an authenticated user, it trusts client-provided order/amount/company/branch values.
- Why it matters: Tenant metadata and company creation need careful abuse controls. Payment checkout initiation must be tied to a verified invoice/subscription record and authenticated authorization, not only a client body.
- Recommended fix: Keep only true signup and payment provider callbacks public. Protect company read endpoints. Ensure payment checkout is only started through subscription/billing services that verify ownership, invoice status, amount, currency, branch, and idempotency.
- Implementation complexity: Medium
- Suggested priority: Phase 0

### Finding 3 - Authorization coverage is manual and therefore fragile

- Severity: High
- Area: Authorization
- Files:
  - `Service/security/AuthorizationService.java:28-64`
  - `Service/security/AuthenticatedEffectiveConfigurationService.java:87-136`
  - Representative covered endpoint: `Controller/posController/OrderController.java:39-105`
  - Representative public/partially covered endpoint: `Controller/CompanyController.java:39-110`
- What is wrong: Capability checks are manually placed in each controller or service. Newer endpoints show good patterns, but a missing call silently leaves an endpoint as "authenticated only" or public if present in the security allowlist.
- Why it matters: In a SaaS multi-tenant system, missing one authorization check can create cross-tenant data access or privilege escalation.
- Recommended fix: Introduce a small annotation/aspect, e.g. `@RequireCapability("pos.sale.read")`, with standard tenant/branch extraction. Keep explicit service-level checks for high-risk operations.
- Implementation complexity: Medium
- Suggested priority: Phase 1

### Finding 4 - Dynamic per-branch table DDL is a long-term scalability and safety risk

- Severity: High
- Area: Multi-tenancy / database
- Files:
  - `util/TenantSqlIdentifiers.java:11-420`
  - `DatabaseRequests/DbBranch.java:119-274`
  - `Config/SchemaCompatibilityInitializer.java:53-74`
- What is wrong: The app creates and drops physical per-branch tables at runtime and runs compatibility DDL on startup. Identifiers are sanitized through `TenantSqlIdentifiers`, which is good, but runtime DDL increases operational risk and complicates migrations, indexes, reporting, and tenant consistency.
- Why it matters: Every new branch multiplies table count. Query patterns, backups, migrations, and schema drift become harder as tenants and branches grow. Branch deletion also drops data tables.
- Recommended fix: Freeze new runtime DDL for new domains, prefer shared tenant tables with `company_id` and `branch_id`, keep `TenantSqlIdentifiers` for legacy compatibility, and create a migration path module by module.
- Implementation complexity: Large
- Suggested priority: Phase 2

### Finding 5 - Header and debug logging can expose credentials and PII

- Severity: High
- Area: Logging / security
- Files:
  - `Controller/CompanyController.java:104-110`
  - `Filters/JwtRequestFilter.java:50-78`
  - `src/main/resources/application.properties:132-137`
- What is wrong: `/Company/listHeaders` logs all request headers, including `Authorization`. The JWT filter logs every request with username/context at INFO. Default logging enables DEBUG for app/web/JDBC and trace for AI.
- Why it matters: Tokens, URLs, SQL parameters, usernames, and business data can land in logs. Logs often have broader access and longer retention than application data.
- Recommended fix: Delete the header logging endpoint or redact sensitive headers. Reduce default logging to INFO/WARN. Move DEBUG/trace behind local-only profile. Add a log redaction policy for `Authorization`, tokens, API keys, phone numbers, and AI prompts.
- Implementation complexity: Small
- Suggested priority: Phase 0

### Finding 6 - Large classes and mixed responsibilities slow safe change

- Severity: Medium
- Area: Architecture
- Files:
  - `ai/service/AiChatOrchestratorService.java`: 1453 lines
  - `DatabaseRequests/DbFinanceSetup.java`: 992 lines
  - `Service/finance/FinanceReconciliationService.java`: 988 lines
  - `DatabaseRequests/DbPayroll.java`: 966 lines
  - `DatabaseRequests/DbBillingAdminReadModels.java`: 845 lines
  - `Controller/PlatformAdminController.java`: 772 lines
- What is wrong: Several classes contain many workflows, query builders, mapping, validation, and orchestration in one file.
- Why it matters: Large classes make regression risk higher, especially in finance, payroll, billing, and AI.
- Recommended fix: Split by query/command/validator/mapper/use-case boundaries, starting only where new work is needed.
- Implementation complexity: Medium
- Suggested priority: Phase 1

## 4. Security Findings

Authentication and JWT:

- Positive: Spring Security stateless JWT is configured in `SecurityConfiguration.java:44-78`.
- Positive: Passwords use BCrypt for new writes through `LegacyAwareBcryptPasswordEncoder` and `DbUsers.java:120-126`.
- Risk: `LegacyAwareBcryptPasswordEncoder.java:16-24` still accepts plaintext legacy passwords and upgrades via `UserDetailsPasswordService`. This is pragmatic, but needs an expiry plan and monitoring.
- Risk: `JwtProperties.java:18-24` generates an ephemeral secret when missing. This prevents boot failure but invalidates tokens on restart and can hide production misconfiguration unless prod validation catches it.
- Risk: JWT subject uses legacy `"username : role"` formatting in `MyUserDetailsServices.java:49-54`, forcing repeated string parsing.

Authorization:

- Positive: `AuthorizationService` and `AuthenticatedEffectiveConfigurationService` enforce tenant/branch capability checks and reject branch mismatch.
- Risk: It is manual and not uniformly guaranteed across all controllers.
- Risk: Public company read endpoints expose tenant metadata. Needs verification: whether `/Company/getCompany` and `/Company/getCompanyById` are intentionally public for onboarding.

CORS:

- Positive: CORS origins are property-driven in `SecurityConfiguration.java:106-115`.
- Risk: `application-prod.properties:33-36` includes both production and localhost in default allowed origins. Localhost in production defaults may be acceptable for staging but should not be the default production posture.

Secrets:

- Critical: Committed secret values and source defaults require rotation. Do not only remove them from future commits.
- Positive: Prod properties use environment placeholders for most secrets.

Payments:

- Positive: PayMob callback HMAC validation exists in `Service/PayMobService.java:128-204`, and callback amount/currency/reference are validated in `PayMobService.java:208-259`.
- Risk: `PayMobController.paymentKeyRequest` lacks `@Valid`, `Principal`, and capability checks in `PayMobController.java:53-72`. Needs to be removed or routed through trusted billing flow.

File/object storage:

- Positive: S3 presigned URLs use bounded durations in `Service/S3StorageService.java` according to search results.
- Risk: `S3Properties.java:20-24` logs endpoint/region/bucket. This is usually acceptable, but avoid logging credentials and keep bucket names non-sensitive.

WhatsApp:

- Positive: WhatsApp token encryption uses AES/GCM in `WhatsAppTokenEncryptionService.java:17-59`.
- Risk: `WhatsAppTokenEncryptionService.java:34-40` falls back to a static dev key if not configured. Combined with `application.properties:166` default encryption key, this needs prod-fail validation and removal of insecure defaults.

## 5. Multi-Tenancy Findings

Strengths:

- `TenantSqlIdentifiers` validates positive tenant/branch IDs and safe schema names.
- Tenant schemas follow predictable `c_<companyId>` format, reducing arbitrary identifier injection risk.
- Newer authorization services verify requested tenant/branch context before loading effective capabilities.
- AI tool services call `AiPermissionService.validateToolAccess`, which validates branch access before running data tools.

Risks:

- There are many direct `TenantSqlIdentifiers.*` string interpolation call sites. Even when safe, this pattern needs strict review discipline because SQL identifiers cannot be bound as parameters.
- `DbBranch.deleteBranch` deletes the public branch row and drops multiple tenant tables in `DbBranch.java:130-139`. This is destructive and should require soft-delete/lifecycle controls and backups.
- Branch uniqueness checks are global in `DbBranch.java:76-81`, not scoped by company. This may prevent two companies from using the same branch name.
- `AiSecurityContextResolver.validateBranchAccess` checks branch membership and ownership, but allowed branches are built from company branches in `AiSecurityContextResolver.java:80-88`; this grants all company branches to a company owner/admin context. Needs verification against role and package entitlements.
- Legacy public tables (`public.users`, `public."Branch"`, `public."Company"`) still drive tenant context. That is workable, but cross-table consistency must be tested heavily.

Recommended direction:

- Keep `TenantSqlIdentifiers` as the only identifier construction API for legacy schema/table names.
- Add tests that pass mismatched company/branch IDs to every controller family.
- Add a tenant context object instead of passing raw `companyId`, `branchId`, and `Principal` through many layers.
- Prefer shared tenant tables with branch columns for new modules.

## 6. Database & Performance Findings

SQL access:

- The backend uses JDBC heavily, with around 737 query/update/execute call sites.
- Named parameters are common in newer modules, which is good.
- Dynamic identifiers are common and mostly routed through `TenantSqlIdentifiers`, but runtime DDL remains a high-risk area.

Transactions:

- Positive: Many high-risk services use `@Transactional`, especially finance, billing, company, branch, inventory, and POS offline sync.
- Risk: Some repository methods catch broad `Exception` and return defaults, which can hide data errors. Examples include dashboard providers and legacy database request classes.

Pagination:

- Positive: Many newer modules have explicit `LIMIT/OFFSET`, default limits, and max limits.
- Risk: Legacy endpoints still return raw arrays/lists without consistent pagination, for example branch/company lookup and some POS/dashboards. Needs verification per high-volume endpoint.

Indexes:

- Positive: New branch order/detail tables create some indexes in `DbBranch.java:248-269`.
- Risk: Per-branch physical tables require every schema/table variant to receive consistent indexes. Missing indexes in one provisioning path can hurt only some tenants, making production issues hard to reproduce.
- Suggested index audit: order date/client, order detail order/product, inventory product branch/status/category/barcode, stock ledger product/branch/time, client branch/phone/name, supplier branch/status/name/phone, pricing history product/effective date, AI audit company/user/created date.

Flyway and schema readiness:

- Positive: There are many migrations and migration tests for inventory and AI knowledge.
- Risk: `spring.flyway.enabled` is false in `application.properties:112` and `application-prod.properties:28`, while dev enables Flyway and disables validation in `application-dev.properties:18-19`.
- Risk: `SchemaCompatibilityInitializer` runs DDL outside Flyway. This can drift from migration history.

Performance:

- AI SQL execution sets query timeout and max rows in `AiSqlExecutor.java:38-63`, which is good.
- AI/RAG embedding and retrieval are bounded by config, but external call retries/circuit breakers need review.
- Scheduled jobs exist for FX, billing, platform metrics, offline POS, product import cleanup, and idle connection termination. Distributed locking appears present for FX via `FxSchedulerLockRepository`, but not all scheduled jobs obviously use locks. Needs verification for multi-instance deployments.

## 7. API & Validation Findings

Strengths:

- Bean Validation is used broadly across newer controllers and request models.
- `GlobalExceptionHandler.java:27-131` provides consistent `ApiErrorResponse` handling for validation, bad requests, data access, access denied, and unexpected errors.
- Several controllers validate path variables and request bodies, for example `OrderController.java:39-105` and `BranchController.java:58-128`.

Risks:

- API naming is inconsistent: uppercase legacy paths (`/Company`, `/Branch`, `/Order`), newer lowercase paths (`/api/config`, `/api/whatsapp`), and action-style endpoints (`getOrders`, `saveOrder`, `AddBranch`).
- Some endpoints return raw strings or manually built JSON strings, e.g. `BranchController.java:89` and payment checkout response construction in `PayMobController.java:66-72`.
- Some endpoints expose entity/domain models directly, including `Company`, `Branch`, and `Order`.
- `PayMobController.paymentKeyRequest` does not use `@Valid` even though `PaymentTokenRequest` has validation annotations.
- Error details can include database-specific messages in `GlobalExceptionHandler.collectDatabaseDetails` for data integrity violations. Useful in dev, but should be sanitized in production.

Recommended direction:

- Establish `/api/v1/...` conventions for new endpoints.
- Use response DTOs consistently.
- Keep legacy routes stable but place new routes behind consistent naming.
- Add validation to every request body and path/query parameter.
- Add rate limiting to auth, company signup, public catalog, payment checkout/callback, AI chat/SQL, and WhatsApp test-send.

## 8. AI/RAG Findings

Strengths:

- AI tools are permission-gated through `AiPermissionService.java:64-103`.
- AI branch access checks validate both allowed branch IDs and branch/company ownership in `AiSecurityContextResolver.java:61-73`.
- AI SQL validator blocks write operations, wildcard selects, sensitive identifiers, unapproved tables, missing branch/company placeholders, and excessive limits in `AiSqlValidator.java:33-112`.
- AI SQL executor uses read-only transaction, prepared statement substitution, query timeout, and max rows in `AiSqlExecutor.java:29-65`.
- Customer AI repository masks phone numbers in `CustomerAiRepository.java:110-121`.
- AI audit, cost tracking, conversation history, cache, and RAG-related repositories exist.

Risks:

- `AiChatOrchestratorService` is 1453 lines and mixes orchestration, prompts, fallback content, routing, and policy. This is hard to safely evolve.
- AI/RAG is enabled by default in base config (`vls.ai.enabled=true`, `vls.ai.rag.enabled=true`) but disabled in dev/prod defaults for RAG. This environment split needs deliberate deployment documentation.
- AI log levels default to trace in `application.properties:133-134`; prompts, tool args, or retrieved content must never be logged unredacted.
- Tool result limits are present, but token/cost limits should be enforced at the service boundary and tested.
- Needs verification: whether RAG document/chunk tables are tenant-scoped for custom company knowledge, and whether global help knowledge is clearly separated from tenant data.

Recommended AI hardening:

- Keep AI SQL read-only and allowlist-based.
- Add integration tests for cross-tenant AI tool access and branch mismatch.
- Add prompt/tool argument redaction to all AI logs and audit payloads.
- Split `AiChatOrchestratorService` into router, prompt builder, tool executor, RAG context builder, fallback response builder, and response sanitizer.
- Add circuit breaker/retry/backoff around external AI providers and embeddings.

## 9. Testing Gaps

Existing positive coverage:

- 72 test Java files were found.
- Tests exist for suppliers, receipts, serialized inventory, products, PayMob, payment/finance posting, inventory transactions, expenses, finance, Flyway migrations, AI SQL validator, AI knowledge, AI providers, POS offline sync, dynamic pricing, customer behavior, FX, branch, company, WhatsApp, and authorization.

Priority missing or incomplete tests:

1. Tenant isolation integration tests for every controller family with mismatched `companyId`/`branchId`.
2. Public endpoint tests proving only intended unauthenticated routes are reachable.
3. Payment checkout tests proving user cannot create a payment key for another tenant/branch or arbitrary amount.
4. PayMob callback tests for invalid HMAC, amount mismatch, currency mismatch, duplicate event, and unknown order.
5. Branch deletion/provisioning tests around rollback, partial failure, and audit.
6. Flyway validation test for all migrations against PostgreSQL, not only selected modules.
7. AI tool tests for branch access denial and data masking.
8. AI SQL tests for table allowlist bypass attempts, aliases, joins, aggregate edge cases, comments, subqueries, and missing tenant placeholders.
9. Inventory stock movement regression tests for negative stock, serialized units, bounced sales, supplier receipt posting, and branch transfer.
10. Dynamic pricing tests for USD buy price, EGP price, exchange-rate changes, min/max margin, rounding, approval, and audit.
11. Observability tests for request IDs, audit records, and redaction of auth headers/tokens.

## 10. Recommended Target Architecture

Do not rewrite everything. Use this target structure incrementally for new modules and touched legacy modules:

- `controller`: thin REST adapters, request validation, response DTOs only.
- `application` or `service`: use cases, transactions, authorization coordination.
- `domain`: business rules, domain services, state machines, calculators.
- `repository`: JDBC gateways only, no business decisions.
- `dto`: request/response DTOs.
- `mapper`: row mappers and DTO/domain mapping.
- `security`: authentication, capability checks, tenant context, policy annotations.
- `tenant`: tenant context resolution, SQL identifiers, branch/company ownership verification.
- `config`: typed properties and startup validators.
- `exception`: API errors and safe exception mapping.
- `audit`: audit events and redaction.
- `ai`: AI router, prompts, tools, RAG, SQL agent, cost, audit, provider clients.
- `jobs`: scheduled/background workflows with locks.

For legacy modules, prefer this sequence:

1. Add tests around current behavior.
2. Extract validators and mappers.
3. Move raw SQL to repository classes.
4. Add authorization annotations/central guards.
5. Keep route compatibility until frontend migration is complete.

## 11. Prioritized Roadmap

### Phase 0: Critical safety fixes

Task: Remove and rotate committed secrets

- Files likely affected: `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`, `config/application-dev.local.properties`, `.gitignore`
- Risk: Breaking local/dev startup if env vars are missing
- Estimated effort: 0.5-1 day plus credential rotation
- Acceptance criteria: no real key material in repo; prod boot fails on missing critical secrets; local sample file contains placeholders only; rotated PayMob/S3/AI credentials confirmed.

Task: Remove header logging and reduce default logging

- Files likely affected: `Controller/CompanyController.java`, `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`
- Risk: Low
- Estimated effort: 0.5 day
- Acceptance criteria: no endpoint logs all headers; `Authorization` is redacted everywhere; base logging not DEBUG/trace by default.

Task: Tighten public security allowlist

- Files likely affected: `SecurityPack/SecurityConfiguration.java`, `Controller/CompanyController.java`, tests
- Risk: Frontend signup/onboarding may need route adjustment
- Estimated effort: 1 day
- Acceptance criteria: only intended signup/public/catalog/callback endpoints are unauthenticated; company read endpoints require auth unless explicitly documented public.

Task: Secure payment checkout initiation

- Files likely affected: `OnlinePayment/OPController/PayMobController.java`, `Service/SubscriptionService.java`, `Service/payment/*`, tests
- Risk: Billing flow regression
- Estimated effort: 1-2 days
- Acceptance criteria: checkout is created only for verified billing/subscription records; request body is validated; tenant/branch/amount/currency cannot be client-forged.

### Phase 1: Backend structure cleanup

Task: Introduce capability guard annotation/aspect

- Files likely affected: `Service/security`, controllers, tests
- Risk: Medium, because incorrect tenant/branch parameter extraction can deny valid requests
- Estimated effort: 2-4 days
- Acceptance criteria: common controllers use declarative capability checks; tests cover allow/deny and tenant mismatch.

Task: Split large classes opportunistically

- Files likely affected: `AiChatOrchestratorService`, `DbFinanceSetup`, `FinanceReconciliationService`, `DbPayroll`, `PlatformAdminController`
- Risk: Medium
- Estimated effort: incremental, 1-3 days per class area
- Acceptance criteria: no behavior changes; tests pass; new classes have single responsibilities.

Task: Standardize API responses for new routes

- Files likely affected: controllers returning raw strings/manual JSON
- Risk: Low if legacy routes are preserved
- Estimated effort: 1-2 days
- Acceptance criteria: new routes return DTOs; legacy response format remains compatible where needed.

### Phase 2: Multi-tenancy and SQL hardening

Task: Add tenant isolation test suite

- Files likely affected: `src/test/java/...`
- Risk: Low
- Estimated effort: 2-4 days
- Acceptance criteria: cross-tenant and cross-branch mismatch tests cover POS, inventory, customers, suppliers, finance, pricing, AI, and admin APIs.

Task: Build dynamic SQL inventory and allowlist policy

- Files likely affected: `TenantSqlIdentifiers`, repository classes, docs
- Risk: Low
- Estimated effort: 1-2 days
- Acceptance criteria: every dynamic identifier call goes through approved helpers; deprecated public offline table helpers are not used by runtime paths.

Task: Create migration strategy away from per-branch physical tables

- Files likely affected: POS order/product/inventory/supplier repositories, migrations
- Risk: High
- Estimated effort: multi-sprint
- Acceptance criteria: a written migration plan exists; new modules use shared tenant tables; no new per-branch physical tables are introduced.

### Phase 3: Performance and scalability

Task: Query/index audit for high-volume modules

- Files likely affected: migrations and repository query files
- Risk: Medium
- Estimated effort: 3-5 days
- Acceptance criteria: explain plans captured for top queries; missing indexes added via migrations; pagination caps enforced.

Task: Scheduled job multi-instance hardening

- Files likely affected: FX, billing, platform metrics, offline worker, import cleanup jobs
- Risk: Medium
- Estimated effort: 2-4 days
- Acceptance criteria: every job is idempotent or locked; duplicate execution tests exist.

Task: Add async/batch boundaries for expensive work

- Files likely affected: AI ingestion, embeddings, imports, billing retries, offline POS posting
- Risk: Medium
- Estimated effort: 3-7 days
- Acceptance criteria: long-running jobs do not block request threads; retries are bounded; status is observable.

### Phase 4: Testing and observability

Task: PostgreSQL integration test profile

- Files likely affected: `pom.xml`, test config, Flyway tests
- Risk: Medium
- Estimated effort: 2-4 days
- Acceptance criteria: migrations validate against PostgreSQL in CI or a documented local command; repository tests use realistic database behavior.

Task: Add request correlation and structured audit logging

- Files likely affected: filters/interceptors, audit services, logging config
- Risk: Low/Medium
- Estimated effort: 2-3 days
- Acceptance criteria: each request has correlation ID; tenant/user/branch are present in audit context; sensitive values are redacted.

Task: Add metrics and health checks

- Files likely affected: `pom.xml`, config, actuator/security, service metrics
- Risk: Medium
- Estimated effort: 2-4 days
- Acceptance criteria: health endpoint is safe; DB/Redis/S3/AI status visible; AI token/cost metrics exported.

### Phase 5: AI/RAG production hardening

Task: Split AI orchestration and add provider resilience

- Files likely affected: `ai/service/AiChatOrchestratorService.java`, provider clients, tests
- Risk: Medium
- Estimated effort: 3-7 days
- Acceptance criteria: orchestrator is decomposed; provider timeouts/retries/circuit breakers are tested; fallback behavior is deterministic.

Task: Harden AI audit and RAG tenant scoping

- Files likely affected: `ai/knowledge`, `ai/rag`, `ai/audit`, tests
- Risk: Medium
- Estimated effort: 2-5 days
- Acceptance criteria: tenant-private knowledge cannot leak; global help knowledge is separated; logs/audit payloads redact secrets and PII.

Task: Expand AI SQL red-team tests

- Files likely affected: `AiSqlValidatorTest`, `AiSqlExecutor` tests
- Risk: Low
- Estimated effort: 1-2 days
- Acceptance criteria: forbidden operations, aliases, subqueries, comments, missing filters, sensitive columns, and oversized results are covered.

## 12. Codex Implementation Prompts

### Security hardening prompt

You are a senior backend security engineer. In the `valueinsoft-backendv2` Spring Boot repo, implement Phase 0 security hardening only. Remove committed secret defaults from application property files, add safe placeholder examples, ensure local secret files are ignored, remove or redact `/Company/listHeaders`, reduce default logging from DEBUG/trace, and add startup validation that fails production boot when required JWT, PayMob, S3, WhatsApp, and AI secrets are missing. Do not change business behavior. Add focused tests where practical. Provide a concise summary and list any credentials that must be rotated without printing secret values.

### Tenant isolation hardening prompt

You are a senior SaaS multi-tenancy engineer. Inspect `AuthorizationService`, `AuthenticatedEffectiveConfigurationService`, `TenantSqlIdentifiers`, controllers, and repositories. Add a focused tenant isolation test suite proving users cannot access mismatched company or branch data across POS, inventory, customers, suppliers, finance, pricing, AI tools, and platform admin. Do not rewrite the tenancy model yet. Fix only confirmed gaps. Preserve legacy routes.

### Database performance improvements prompt

You are a senior PostgreSQL performance engineer. Audit high-volume JDBC queries in POS orders, order details, inventory products, stock ledger, clients, suppliers, pricing history, finance reporting, and AI audit. Propose and implement only low-risk index migrations and pagination caps. Use Flyway migrations. Do not add runtime DDL. Include before/after rationale for each index and add tests for query builders where available.

### API validation cleanup prompt

You are a senior Spring API engineer. Standardize validation and response DTO usage for payment checkout, company read routes, branch creation, and POS order endpoints. Add `@Valid`, path/query constraints, safe DTO responses, and consistent error responses. Preserve legacy routes and response compatibility where the frontend likely depends on it. Add controller tests for invalid inputs.

### Testing setup prompt

You are a senior test engineer. Add a PostgreSQL-backed integration test profile for `valueinsoft-backendv2`, preferably using Testcontainers if compatible with the project environment. Ensure Flyway migrations can be validated in tests. Add smoke tests for authentication, capability authorization, tenant mismatch denial, PayMob callback HMAC validation, and AI SQL validation. Keep tests deterministic and avoid real external API calls.

### AI/RAG hardening prompt

You are a senior AI platform engineer. Harden the AI/RAG implementation without changing product behavior. Add tests for AI tool tenant/branch scoping, AI SQL red-team cases, RAG tenant/global knowledge separation, sensitive data redaction, provider timeout/fallback behavior, and token/cost controls. Split `AiChatOrchestratorService` only where tests make the extraction safe.

### Observability setup prompt

You are a senior production observability engineer. Add request correlation IDs, safe structured logs, audit context, redaction for headers/tokens/API keys/phone numbers, and basic metrics for request latency, database failures, AI usage/cost, payment callbacks, scheduled jobs, and offline POS processing. Add safe health checks for DB, Redis, S3, and AI providers. Ensure observability endpoints are not publicly exposed in production.

