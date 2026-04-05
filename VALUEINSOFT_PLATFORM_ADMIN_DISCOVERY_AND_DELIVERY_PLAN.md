# VALUEINSOFT PLATFORM ADMIN DISCOVERY AND DELIVERY PLAN

Prepared from a full documentation-first review, followed by backend and frontend discovery.

Scope:
- markdown-first analysis
- backend architecture inventory
- frontend architecture inventory
- class-level admin-relevant assessment
- gap analysis for a market-standard platform admin dashboard
- phased delivery plan

Companion documents:
- `VALUEINSOFT_PLATFORM_ADMIN_BACKEND_IMPLEMENTATION_SPEC.md` for backend-only endpoint, DTO, service, authorization, and rollout planning
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_1_DATABASE_FOUNDATION.md` for implemented database-layer platform-admin work
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_2_READ_APIS.md` for implemented read-only platform-admin backend APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_3_LIFECYCLE_CONTROL.md` for implemented platform lifecycle control work
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_4_AUDIT_AND_SUPPORT_APIS.md` for implemented platform audit and support backend APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_5_CONFIGURATION_AND_COMPANY360_DETAILS.md` for implemented configuration inspection and company 360 detail APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_6_BILLING_AND_DOMAIN_DETAILS.md` for implemented billing summary and company 360 client and product detail APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_7_FINANCE_AND_SUBSCRIPTION_ANALYTICS.md` for implemented subscription analytics detail and company 360 finance inspection APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_8_REVENUE_TREND_AND_DAILY_METRICS.md` for implemented daily metric refresh and snapshot-backed revenue trend APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_9_SCHEDULED_METRICS_AND_OVERVIEW_WIDGETS.md` for implemented scheduled metric refresh and snapshot-backed overview enrichment
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_10_OVERVIEW_ALERTING_AND_METRICS_OBSERVABILITY.md` for implemented overview alerting and metrics refresh observability APIs
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_11_OVERVIEW_RECENT_ACTIONS_AND_ANOMALY_ALERTS.md` for implemented overview recent admin actions and anomaly-style alerting
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_12_ALERT_ACKNOWLEDGMENT_AND_THRESHOLD_CONFIG.md` for implemented alert acknowledgment workflow and threshold configuration
- `VALUEINSOFT_PLATFORM_ADMIN_STAGE_13_SCOPED_ALERT_ACKNOWLEDGMENTS_AND_NOTIFICATION_HOOKS.md` for implemented scoped alert acknowledgments, history APIs, and notification outbox hooks

Important interpretation rules used in this document:
- "Exists now" means code or docs were found directly.
- "Inferred" means a conclusion drawn from multiple concrete sources.
- "Proposed" means a recommendation, not a current implementation.

---

## SECTION A - Executive Summary

- The system is a multi-tenant business operations platform with public marketing, authentication, onboarding, company and branch runtime, POS, inventory, finance, suppliers, clients, reporting, subscriptions, and an emerging configuration-driven authorization model.
- The platform already has strong tenant runtime foundations and a meaningful tenant configuration layer.
- The strongest current admin surface is tenant-scoped, not platform-scoped.
- Backend readiness for a true Platform Operations Console is low to medium-low.
- Frontend readiness for a true Platform Operations Console is low.
- The main strategic strength is the effective configuration and capability model.
- The main strategic weakness is the split storage model:
  - shared public metadata tables
  - dynamic per-company schemas
  - dynamic per-branch operational tables
- This architecture works for tenant runtime, but it is the main friction point for platform-wide reporting, safe cross-tenant operations, and market-standard admin observability.

Current maturity:
- Tenant runtime: medium to medium-high
- Tenant configuration and authorization foundation: medium
- Platform admin operations: low
- Platform analytics and governance: low

Admin readiness level:
- Not ready yet for a market-standard SaaS operations console
- Ready enough to support an MVP read-only platform admin module if built on top of the current configuration and tenant models

---

## SECTION B - Markdown-Derived Architecture Summary

### What The Docs Say

- The docs describe a configuration-driven system where backend is authoritative for:
  - capabilities
  - enabled modules
  - workflow flags
  - navigation eligibility
  - effective configuration
- The docs define a layered resolution model:
  - platform defaults
  - package or plan policies
  - company template defaults
  - tenant overrides
  - role grants
  - user overrides
  - branch and session scope
  - runtime prerequisites
- The docs define effective configuration as a single resolved contract for frontend consumption.
- The docs define packages, company templates, modules, capabilities, navigation, and workflow flags as first-class platform concepts.
- The docs describe the frontend target state as metadata-driven:
  - route metadata
  - navigation schema
  - capability-checked rendering
  - config-driven shell visibility

### Roadmap Direction

- Modernization is intended to be incremental, not a rewrite.
- Existing business modules should be preserved and wrapped in better authorization and configuration contracts.
- Backend authorization should move from legacy role assumptions to capability enforcement.
- Frontend should stop hardcoding role behavior and render from effective configuration.
- Tenant administration should be dynamic and reusable.
- Platform administration should become a separate module, not an extension of tenant pages.

### Important Constraints From Docs

- Backend must remain the final authority for authorization.
- Frontend should not invent access rules.
- Effective configuration should be the runtime source of truth.
- The system should support company, branch, and self scopes.
- Packages and templates must affect runtime availability of modules and workflows.
- The roadmap assumes reuse of current company, branch, and user models.

### Docs-Derived System Map

- Public layer:
  - marketing pages
  - pricing
  - workshop
  - registration
  - login
- Auth layer:
  - username and password authentication
  - JWT-based session
- Tenant foundation:
  - company creation
  - branch creation
  - onboarding state
- Configuration foundation:
  - platform modules
  - platform capabilities
  - role definitions
  - role grants
  - package plans
  - package module policies
  - company templates
  - template module defaults
  - template workflow defaults
  - tenant module overrides
  - tenant workflow overrides
  - tenant role assignments
  - tenant user grant overrides
- Runtime app:
  - dashboard
  - clients
  - suppliers
  - inventory
  - POS
  - finance
  - profile
  - company settings
  - users
- Admin concepts:
  - tenant company control center exists conceptually and practically
  - platform web admin exists conceptually, but implementation is weak

### Important Docs-To-Code Tension

- The docs are ahead of the code in several areas.
- The docs describe a more complete metadata-driven shell and platform admin shape than the current implementation.
- The codebase is still hybrid:
  - old role-based assumptions
  - new capability model
  - old screens and route structures
  - new domain-based architecture pieces

---

## SECTION C - Backend Inventory

### Major Packages And Classes

Core packages:
- `Controller`
- `Service`
- `DatabaseRequests`
- `SecurityPack`
- `Filters`
- `Model`
- `Config`
- `util`
- `ExceptionPack`
- `OnlinePayment`

Important controllers:
- `AuthenticateController`
- `CompanyController`
- `BranchController`
- `UserController`
- `ClientController`
- `SupplierController`
- `ConfigurationController`
- `ConfigurationAdministrationController`
- `AppSubscriptionController`
- `ExpensesController`
- `ClientReceiptController`
- `SupplierReceiptController`
- `ProductController`
- `CategoryController`
- `OrderController`
- `InventoryTransactionController`
- `ShiftPeriodController`
- `DamagedItemController`
- `SlotsFixAreaController`
- `DVCompanyAnalysisController`
- `DVSalesCompanyController`
- `DvSalesController`
- `WebAdminController`

Important services:
- `AuthenticatedEffectiveConfigurationService`
- `EffectiveConfigurationService`
- `AuthorizationService`
- `ConfigurationAdministrationService`
- `CompanyService`
- `BranchService`
- `ClientService`
- `SupplierService`
- `ProductService`
- `OrderService`
- `InventoryTransactionService`
- `ShiftPeriodService`
- `ExpensesService`
- `ClientReceiptService`
- `SupplierReceiptService`
- `SalesAnalyticsService`
- `CompanyAnalysisService`
- `SubscriptionService`
- `PayMobService`

Important database request classes:
- `DbUsers`
- `DbCompany`
- `DbBranch`
- `DbClient`
- `DbSupplier`
- `DbConfigurationAdmin`
- `DbTenants`
- `DbPackagePlans`
- `DbCompanyTemplates`
- `DbPlatformModules`
- `DbPlatformCapabilities`
- `DbRoleDefinitions`
- `DbRoleGrants`
- `DbTenantRoleAssignments`
- `DbTenantUserGrantOverrides`

Important security and utility classes:
- `SecurityConfiguration`
- `JwtRequestFilter`
- `MyUserDetailsServices`
- `JwtUtil`
- `TenantSqlIdentifiers`
- `SchemaCompatibilityInitializer`
- `GlobalExceptionHandler`

### Request Flow

- Requests go through Spring Security with JWT stateless auth.
- `JwtRequestFilter` resolves the token and sets authentication context.
- Controllers call services.
- Services use database request classes directly.
- Authorization is enforced in controller and service layers via `AuthorizationService`.
- Many domain services still directly coordinate schema-aware SQL operations.

### Auth Flow

Exists now:
- Authentication is username and password against `public.users`.
- JWT subject is based on the authenticated principal.
- Spring authorities are still derived from legacy `userRole`.

Important consequence:
- Security context is still legacy-role-rooted.
- Capability enforcement happens after authentication, not inside token claims.

### How Tenant, Company, And Branch Are Resolved

Exists now:
- User identity comes from JWT principal.
- Tenant resolution is performed in `AuthenticatedEffectiveConfigurationService`.
- Company is inferred from:
  - user branch context
  - or owner company lookup
- Branch is resolved from:
  - requested branch id
  - or user branch
  - or first company branch

Important consequence:
- Tenant context resolution is practical, but still relies on legacy company and branch assumptions.

### How Authorization Is Enforced

Exists now:
- `AuthorizationService` checks effective capabilities.
- Controllers now use capability checks across many modules.
- Self access is separately supported.

Still true:
- Legacy role strings remain in auth bootstrap.
- Some fallback and compatibility assumptions still exist around owners and older flows.

### Whether Capabilities Or Roles Are Used In Code

Exists now:
- Roles are still used in authentication and some legacy flows.
- Capabilities are now heavily used in runtime backend authorization.

Architectural conclusion:
- Backend is hybrid, but moving in the correct direction.
- Authorization model should continue to converge on capabilities.

### How Plans, Packages, And Subscriptions Are Represented

Exists now:
- package plans exist in shared configuration tables
- companies have tenant records tied to packages and templates
- subscriptions are represented in shared tables and payment flows
- payment integration exists through PayMob

Important practical limitation:
- Commercial analytics are not yet modeled as a first-class platform reporting domain.

### How Company And Branch Status Is Stored

Exists now:
- company and branch metadata exist
- branch activity checks exist
- onboarding state exists

Missing or weak:
- no mature platform lifecycle model for:
  - suspended company
  - resumed company
  - locked branch
  - unlocked branch
  - support-managed states

### Whether Audit Logging Exists

Observed now:
- generic exception handling exists
- some source and reason fields exist on configuration writes

Missing:
- no formal audit event table
- no administrative action history model
- no support notes model
- no actor-action-target-result audit layer

### Whether Admin Or Global Admin Endpoints Already Exist

Exists now:
- tenant-scoped config admin endpoints exist under `/api/config/admin/*`
- effective config endpoints exist under `/api/config/me/*`
- legacy company list functionality exists through old company endpoints
- `WebAdminController` exists in name only

Missing:
- no real platform operations endpoint set
- no platform-wide company inspection API family
- no lifecycle control API family
- no platform audit or support API family

### Important Data Entities

Shared public tables and concepts observed:
- companies
- branches
- users
- company subscriptions
- tenants
- package plans
- package module policies
- company templates
- template module defaults
- template workflow defaults
- platform modules
- platform capabilities
- role definitions
- role grants
- tenant module overrides
- tenant workflow overrides
- tenant role assignments
- tenant user grant overrides
- onboarding states

Dynamic company schema entities observed:
- company users
- shift periods
- supplier receipts
- supplier bought products
- damaged items
- category payload storage
- main majors
- client receipts
- company analysis
- fix area
- clients

Dynamic per-branch entities observed:
- products
- orders
- order details
- supplier tables
- inventory transactions

### Admin-Relevant Observations

- The backend has enough foundation to support a tenant control center.
- The backend does not yet have enough dedicated platform read models or lifecycle APIs to support a market-standard admin console safely.
- The empty `WebAdminController` is a signal that platform admin was intended, but not built.
- The most reusable backend foundation for platform admin is:
  - effective configuration resolution
  - authorization service
  - company and branch repositories
  - package and template repositories
  - subscription services and tables

---

## SECTION D - Frontend Inventory

### Major Modules, Pages, And Components

Core runtime and routing:
- `App.js`
- `AppRuntimeContext.js`
- `authSession.js`
- `PrivateRoute.js`
- `PublicOnlyRoute.js`

Configuration and metadata:
- `appRouteMetadata.js`
- `appNavigationSchema.js`
- `accessResolver.js`
- `configurationApi.js`

Shell and navigation:
- `Layout.js`
- `Aside.js`
- `Main.js`
- `NavBar.js`
- `appShellAccess.js`

Tenant admin:
- `CompanyAdminPortalPage.js`
- `companyAdminApi.js`

Legacy platform admin:
- `WebAdminHomePage.js`
- `WebAdminMainPage.js`
- `WebAdminBranchsDetailsOfCompany.js`

Business pages:
- dashboard
- company settings
- create company
- add branch
- users
- profile
- clients
- suppliers
- inventory
- POS
- finance
- online invoice

### Route And Navigation Observations

Exists now:
- top-level routing is still declared manually in `App.js`
- route metadata exists but is not the live route source
- navigation schema exists but the live sidebar is still hardcoded
- shell view access is still manually mapped
- main view rendering is still largely based on manual view ids

Important consequence:
- Frontend modernization has started, but the live shell is still hybrid.

### Role-Based And Capability-Based Checks

Exists now:
- capability-based checks are present in runtime config and route guards
- role-based fallback still exists in:
  - auth bootstrap
  - default route logic
  - some shell and navbar behavior
  - compatibility helpers

Architectural conclusion:
- Frontend is not yet purely config-driven.

### Runtime Configuration Usage

Exists now:
- frontend fetches effective configuration and navigation from backend
- capability sets and module sets are derived in runtime context
- workflow flags are consumed
- allowed branches are derived from assignments and overrides

Important limitation:
- some owner and role fallbacks still influence branch and route behavior

### Company And Branch Context Handling

Exists now:
- company and branch are stored in legacy session storage
- runtime context also hydrates and normalizes them from effective config
- branch availability is influenced by resolved assignments and overrides

Important limitation:
- branch access behavior can still be confusing when company-scoped grants coexist with branch-scoped grants

### Data Tables And Dashboards

Exists now:
- multiple table-based operational pages exist
- tenant control center has rich admin-style tables
- legacy web admin page has shallow company list and branch detail view

Missing:
- no proper platform analytics dashboards
- no company 360 admin workspace
- no support or audit timeline UI

### Admin-Relevant Observations

- The tenant control center is the strongest reusable admin UI foundation.
- The current web admin UI should be replaced, not extended deeply.
- Shared UI patterns for filters, tabs, tables, badges, and action controls can be reused from the company admin portal.
- Platform admin should be a separate module in the frontend, not another tab inside company control center.

---

## SECTION E - Current Gaps

### Missing Backend Pieces

- no platform admin service layer
- no platform admin controller layer
- no platform-wide overview metrics API
- no company 360 API
- no branch lifecycle control API
- no company lifecycle control API
- no audit event store
- no support notes model
- no platform-wide user, client, product, and revenue read models
- no explicit governance around platform-side inspection and intervention workflows

### Missing Frontend Pieces

- no dedicated platform admin module
- no true platform route tree
- no mature platform navigation schema actually driving runtime
- no company 360 platform page
- no platform analytics dashboard
- no support operations UI
- no audit inspection UI
- no safe operational action UX for suspend, resume, lock, unlock

### Missing Data And Reporting Pieces

- no tenant-level daily metrics table
- no branch-level daily metrics table
- no normalized collections reporting
- no MRR-like revenue trend model
- no churn or suspension trend model
- no onboarding health dashboard
- no support workload model

### Security And Governance Gaps

- authentication still depends on legacy user roles
- frontend still has role fallbacks
- some public endpoints are broader than ideal
- no platform-grade audit trail for admin actions
- no formal separation between tenant admin and platform admin responsibilities
- no explicit break-glass or support impersonation governance model

---

## SECTION F - Proposed Admin Dashboard Plan

### Proposed Platform Operations Console

This section is proposed.

Create a separate platform-level module named:
- `Platform Operations Console`

Do not place it inside the tenant company control center.

### Proposed Modules

- Overview
- Companies
- Company 360
- Branch Operations
- Users
- Commercial
- Configuration Inspector
- Audit
- Support

### Proposed Pages

#### Overview

Widgets:
- total companies
- active companies
- suspended companies
- active branches
- locked branches
- plan distribution
- unpaid subscriptions
- recent collections
- onboarding stuck tenants
- recent admin actions

Filters:
- date range
- package plan
- template
- company status

#### Companies

Columns:
- company id
- company name
- owner
- package plan
- template
- status
- branch count
- user count
- created at
- latest payment state

Actions:
- open company 360
- suspend
- resume
- inspect effective configuration
- open support notes

#### Company 360

Tabs:
- Summary
- Branches
- Users
- Clients
- Products
- Finance
- Subscriptions
- Configuration
- Lifecycle
- Audit
- Support

Summary widgets:
- active branches
- total users
- total clients
- total products
- subscription status
- recent revenue
- recent operational alerts

#### Branch Operations

Columns:
- branch id
- branch name
- company
- branch status
- user count
- module profile
- last shift state
- last transaction activity

Actions:
- lock branch
- unlock branch
- inspect branch access model
- inspect branch configuration

#### Commercial

Views:
- plan adoption
- collections
- revenue trend
- company subscription health
- expiring or unpaid subscriptions

#### Configuration Inspector

Views:
- tenant effective config
- module overrides
- workflow overrides
- role assignments
- user overrides
- resolved capabilities

#### Audit

Views:
- admin actions timeline
- company lifecycle events
- branch lifecycle events
- configuration changes
- support actions

#### Support

Views:
- support notes
- escalation history
- company issue summary
- operational checklists

### Proposed Permissions

This section is proposed.

Platform capabilities:
- `platform.admin.read`
- `platform.admin.write`
- `platform.company.read`
- `platform.company.lifecycle.write`
- `platform.branch.lifecycle.write`
- `platform.configuration.read`
- `platform.configuration.write`
- `platform.billing.read`
- `platform.audit.read`
- `platform.support.read`
- `platform.support.write`

Principles:
- platform admin permissions must be separate from tenant admin permissions
- company owners must not automatically receive platform permissions
- backend must enforce every platform action

### Proposed Backend APIs

This section is proposed.

Read APIs:
- `GET /api/platform-admin/overview`
- `GET /api/platform-admin/companies`
- `GET /api/platform-admin/companies/{tenantId}`
- `GET /api/platform-admin/companies/{tenantId}/branches`
- `GET /api/platform-admin/companies/{tenantId}/users`
- `GET /api/platform-admin/companies/{tenantId}/clients`
- `GET /api/platform-admin/companies/{tenantId}/products`
- `GET /api/platform-admin/companies/{tenantId}/subscriptions`
- `GET /api/platform-admin/companies/{tenantId}/config`
- `GET /api/platform-admin/companies/{tenantId}/audit`
- `GET /api/platform-admin/billing/summary`
- `GET /api/platform-admin/audit/events`
- `GET /api/platform-admin/support/notes`

Write APIs:
- `POST /api/platform-admin/companies/{tenantId}/suspend`
- `POST /api/platform-admin/companies/{tenantId}/resume`
- `POST /api/platform-admin/branches/{branchId}/lock`
- `POST /api/platform-admin/branches/{branchId}/unlock`
- `POST /api/platform-admin/support/notes`
- `POST /api/platform-admin/configuration/refresh-metrics`

### Proposed Data Model Changes

This section is proposed.

Add shared platform read-model tables:
- `tenant_daily_metrics`
- `branch_daily_metrics`
- `platform_admin_audit_log`
- `platform_support_notes`
- `tenant_lifecycle_events`
- `branch_lifecycle_events`

Keep existing source-of-truth tables:
- companies
- branches
- users
- tenants
- package plans
- subscriptions
- configuration tables

### Proposed Audit Requirements

- every platform action must log actor
- every platform action must log capability used
- every platform action must log tenant and branch targets
- every platform action must log payload summary or diff
- every platform action must log result
- read access to highly sensitive operational state should also be logged

---

## SECTION G - Recommended Delivery Plan

### Phase 1 - MVP Read-Only Platform Admin

- create a separate platform admin backend module
- create a separate platform admin frontend route tree
- deliver:
  - overview page
  - companies list
  - company 360 summary
  - branches tab
  - users tab
  - subscriptions snapshot
  - configuration inspector
- reuse:
  - effective configuration service
  - company and branch repositories
  - package and template repositories
  - existing tenant control center UI patterns

### Phase 2 - Operational Control

- add company lifecycle model
- add branch lifecycle model
- implement:
  - suspend company
  - resume company
  - lock branch
  - unlock branch
- add audit log table and service
- add support notes

### Phase 3 - Commercial Analytics

- add tenant and branch daily metrics
- add revenue and collections reporting
- add plan and subscription analytics
- expose commercial dashboard pages

### Phase 4 - Governance Hardening

- reduce legacy role coupling in auth and frontend session behavior
- add integration tests for platform operations
- harden public endpoint exposure
- add operator workflow controls and admin-safe validation
- formalize support governance and escalation logging

### MVP First Principle

- start with read-only platform visibility
- add controlled writes only after audit and lifecycle models exist
- do not build platform operations directly on top of live tenant runtime screens

---

## SECTION H - Risks And Best Practices

### Architecture Risks

- dual storage model complicates platform aggregation
- tenant operational data is distributed across dynamic schemas and tables
- direct live cross-tenant querying will become brittle

### Performance Risks

- platform-wide drilldowns into products, clients, orders, and finance may become expensive
- company 360 should not depend on raw runtime joins across every tenant schema

### Security Risks

- legacy role strings still influence authentication and some frontend behavior
- platform admin must not inherit tenant owner assumptions
- UI hiding must never be treated as authorization

### Data Isolation Risks

- platform views must deliberately cross tenant boundaries
- this increases blast radius if authorization is not explicit and audited

### Maintainability Risks

- frontend remains hybrid between old and new structures
- backend mixes legacy runtime models with newer configuration models
- platform admin built carelessly could deepen this complexity

### Best Practice Recommendations

- extend existing configuration and authorization services instead of rewriting them
- keep backend authoritative for every admin action
- build platform admin as a separate module, not a tenant page
- add read-model tables for platform reporting
- add audit before adding sensitive operational controls
- replace the legacy web admin page with a proper platform console
- keep tenant admin and platform admin clearly separated

---

## Final Architectural Position

- The current system is a viable foundation for a Platform Operations Console.
- The correct approach is controlled extension, not rewrite.
- The first deliverable should be a read-only platform admin module backed by explicit platform APIs.
- The second deliverable should be lifecycle control plus auditability.
- The third deliverable should be commercial and operational analytics.
- Governance and isolation hardening should remain mandatory throughout the rollout.
