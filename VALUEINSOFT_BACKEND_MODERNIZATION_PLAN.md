# Valueinsoft Backend Modernization Plan

## Purpose

This document defines the staged modernization plan for the Valueinsoft backend.

The goal is not to rewrite the system. The goal is to evolve the existing Spring Boot and PostgreSQL backend into a production-grade, secure, maintainable, and scalable platform without breaking current business flows.

## Architecture Constraints

The plan assumes these constraints remain in place during modernization:

- Java 17
- Spring Boot 2.5.4
- Spring Security
- PostgreSQL
- Spring JDBC and raw JDBC coexist today
- multi-tenant company schemas such as `C_<companyId>`
- branch-specific tables such as `PosProduct_<branchId>` and `PosOrder_<branchId>`
- no JPA rewrite
- incremental refactor only

## Modernization Principles

- preserve current working behavior where possible
- improve security and correctness before adding features
- refactor incrementally instead of rewriting modules wholesale
- keep tenant schema logic intact while isolating dynamic SQL safely
- move business logic toward services and away from controllers and utility classes
- use parameterized queries everywhere possible
- add transactions around critical multi-step financial and stock flows

## Phase 1: Essential Security and Stability

Status:

- completed on 2026-03-29
- verified with `./mvnw.cmd -q -DskipTests compile`

Scope completed:

1. Password hashing modernization
- replaced `NoOpPasswordEncoder` with a legacy-aware BCrypt encoder
- implemented lazy migration so existing plaintext passwords still authenticate once and are then upgraded to BCrypt
- updated user creation and password reset flows to store BCrypt hashes only
- wired password upgrading through `UserDetailsPasswordService`

2. Secrets and runtime configuration externalization
- moved datasource settings to Spring environment-backed properties
- moved JWT secret and expiration to environment-backed properties
- moved PayMob auth token and integration identifiers to environment-backed properties
- split configuration into common, `dev`, and `production` property files
- removed hard-coded runtime secrets from the main application, JWT utility, and payment integration path

3. CORS and profile cleanup
- standardized profile names to `dev` and `production`
- centralized allowed origins in Spring Security CORS configuration
- removed controller-level wildcard `@CrossOrigin("*")` usage so one policy is authoritative
- locked the intended origins to localhost in development and Dierlo domains in production through config

4. Global API error handling
- added a structured API error model with `timestamp`, `status`, `code`, `message`, `path`, and optional `details`
- added a global exception handler for authentication, bad requests, not found, conflicts, data access issues, and unexpected failures
- kept successful response payloads and endpoint paths backward compatible in the touched modules

5. Safe SQL improvements in touched modules
- added a shared tenant SQL identifier validator
- refactored `DbUsers`, `DbExpenses`, and `DBMSupplierReceipt` toward validated parameterized SQL
- kept dynamic schema and table names validated before SQL construction
- preserved the larger order-transaction redesign for Phase 2

Files changed in the implementation:

- `SecurityPack/SecurityConfiguration.java`
- `SecurityPack/MyUserDetailsServices.java`
- `SecurityPack/LegacyAwareBcryptPasswordEncoder.java`
- `DatabaseRequests/DbUsers.java`
- `Controller/AuthenticateController.java`
- `Controller/UserController.java`
- `util/JwtUtil.java`
- `Config/JwtProperties.java`
- `Config/CorsProperties.java`
- `OnlinePayment/PayMobProperties.java`
- `OnlinePayment/OPController/PayMobController.java`
- `SqlConnection/ConnectionPostgres.java`
- `DatabaseRequests/DbPOS/DbExpenses.java`
- `DatabaseRequests/DbMoney/DBMSupplierReceipt.java`
- `util/TenantSqlIdentifiers.java`
- `ExceptionPack/ApiException.java`
- `ExceptionPack/ApiErrorResponse.java`
- `ExceptionPack/GlobalExceptionHandler.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-production.properties`

Remaining risks after Phase 1:

- legacy raw JDBC and string-built SQL still exist in modules outside the touched scope, especially order, supplier, company, and analytics flows
- critical transaction boundaries are still missing in order creation and other multi-step write flows
- structured logging is not complete across the codebase yet
- request DTO validation is still missing in many controllers
- tracked `target/` build output remains a repository hygiene issue

## Phase 2: Foundation Architecture and Data Safety

Status:

- started on 2026-03-29
- partially completed for the order and finance/payment write slices
- verified with `.\mvnw.cmd -q -DskipTests compile`

Completed so far in Phase 2:

1. service-layer and transaction boundary for the order write path
- added `OrderService` to own order creation and bounce-back orchestration
- moved the critical order write path behind Spring `@Transactional`
- added explicit transaction-manager wiring in Spring config instead of relying on implicit behavior

2. safer repository cleanup for the touched order module
- refactored `DbPosOrder` away from the old string-built multi-statement order save path
- replaced the touched order and bounce-back writes with parameterized Spring JDBC operations
- extended tenant SQL identifier helpers for order, order-detail, product, and shift tables

3. request DTO validation for the touched critical endpoints
- added validated request DTOs for authentication, order creation, order bounce-back, user creation, and password reset
- updated controllers to use `@Valid` request models instead of manual `Map<String, Object>` parsing in the touched write endpoints
- improved the global exception handler so validation failures return structured field-level details

4. structured logging and repository hygiene improvements
- added service/repository logging around the touched order flow
- reduced stale JWT noise from `WARN` to `DEBUG`
- added `target/` to `.gitignore` so new build output is not reintroduced by default

5. transactional supplier receipt and payment handling
- added `SupplierReceiptService` as the business entry point for supplier-receipt writes
- moved supplier-receipt creation, inventory remaining-amount updates, and supplier remaining-balance updates behind one Spring transaction boundary
- refactored the touched supplier receipt repository path to parameterized Spring JDBC operations with validated tenant identifiers

6. transactional expenses and finance write cleanup
- added `ExpensesService` to own create and update behavior for dynamic and static expenses
- replaced the touched loose expense request parsing with validated DTO-driven controller inputs
- kept existing success contracts while moving the touched finance writes behind Spring-managed service orchestration

7. subscription and payment side-effect stabilization
- added `SubscriptionService` for branch-subscription creation, branch-activity checks, and payment success handling
- added `PayMobService` so PayMob auth, order creation, payment-key generation, and callback parsing no longer depend on controller-owned static logic
- refactored `DbSubscription` to constructor-injected parameterized Spring JDBC operations
- moved subscription creation and payment-success status updates behind explicit transaction boundaries

8. broader request validation and migration scaffolding
- added validated DTOs for supplier receipts, expenses, branch subscriptions, and payment-token requests
- extended validation coverage across the touched money, subscription, and payment controllers
- added Flyway scaffolding and a baseline placeholder migration while keeping Flyway disabled by default until curated migrations are ready
- performed the one-time tracked `target/` cleanup from the git index while leaving local build output on disk

9. supplier master-data and inventory-transaction stabilization
- added `SupplierService` so supplier create, update, delete, supplier-sales lookup, and supplier bought-product writes no longer depend on static controller-to-repository calls
- refactored `DbSupplier` to constructor-injected parameterized Spring JDBC operations with validated tenant identifiers
- added `InventoryTransactionService` and refactored `DbPosInventoryTransaction` so inventory-transaction writes and supplier balance side effects run through one transaction boundary instead of string-built multi-statement SQL
- added validated DTOs for supplier create/update, supplier bought-product, inventory transaction create, and inventory transaction query payloads
- preserved the existing supplier and inventory route surface while moving the touched stock/finance write logic behind services

10. company provisioning, branch provisioning, and damaged-item stabilization
- added `CompanyService`, `BranchService`, and `DamagedItemService` so company creation, branch creation, company image update, and damaged-item writes no longer depend on controller-owned orchestration
- added validated DTOs for company create, company image update, branch create, and damaged-item create payloads
- refactored `DbCompany` away from the legacy company write path into parameterized Spring JDBC reads and low-level writes, leaving multi-step provisioning in the service layer
- hardened `DbBranch` so branch creation now fails if branch table provisioning fails instead of silently returning success after a partial setup
- refactored `DbPosDamagedList` to parameterized Spring JDBC operations with validated tenant identifiers and one transactional damaged-item write boundary for damaged record insert, stock decrement, and inventory-transaction side effects

Current Phase 2 remaining scope:

- continue service extraction and transaction handling in the untouched money and stock flows, especially analytics update paths, fix-area writes, and remaining legacy company or inventory side paths
- expand DTO validation across the remaining controllers that still accept loose maps and mixed payload shapes
- turn the Flyway scaffold into curated shared-schema and tenant-provisioning migrations when the change set is ready
- continue repository cleanup in untouched legacy modules so raw JDBC and string-built SQL stop being the default pattern
- complete the broader logging cleanup so business write flows are consistently traceable beyond the touched services

Objectives:

1. introduce a clear service layer across the major modules
2. add `@Transactional` boundaries for:
- order creation
- inventory updates
- supplier payments
- expenses
- subscription and payment side effects where needed
3. introduce request DTOs and Bean Validation for controller payloads
4. prepare migration structure with Flyway or equivalent
5. continue repository cleanup toward consistent Spring-managed access patterns

Expected outcome:

- thin controllers
- business logic centralized in services
- safer rollback behavior in financial and stock-changing flows
- more predictable validation failures
- better maintainability for future features

## Phase 3: Business Completeness

Objectives:

1. improve sales domain completeness
- capture unit cost at sale time
- calculate profit per item

2. improve supplier accounting
- supplier balances
- supplier payments

3. improve inventory controls
- full inventory transaction ledger

4. improve shift closing
- expected versus actual cash
- difference tracking

Expected outcome:

- core business workflows reflect real financial state more accurately
- reporting and later accounting work can rely on better domain data

## Phase 4: Accounting Layer

Objectives:

1. introduce an operational accounting ledger
- debit and credit entries
- account codes for `CASH`, `BANK`, `SALES`, `INVENTORY`, `COGS`, `SUPPLIER_PAYABLE`, and `EXPENSE`

2. centralize posting rules inside `AccountingService`

3. ensure each major business action posts consistently
- sale posts revenue and COGS
- purchase posts inventory and payable
- expense posts cost
- supplier payment reduces payable

Expected outcome:

- the backend can support finance-grade traceability instead of only operational totals

## Phase 5: Reporting

Objectives:

- daily sales summary
- daily profit report
- supplier balance report
- shift closing report

Expected outcome:

- the reporting layer becomes consistent with the underlying operational and accounting data model

## Phase 6: Strong Practice and Operational Discipline

Objectives:

1. add meaningful automated tests for:
- auth
- orders
- suppliers
- expenses

2. add structured logging
3. replace `System.out.println` with logging
4. improve failure traceability and operational debugging

Expected outcome:

- changes become safer to ship
- production debugging becomes faster and more reliable

## Recommended Next Step

Continue Phase 2 by finishing the remaining legacy write and validation surface around the now-stabilized core flows:

1. clean the untouched analytics, fix-area, and remaining legacy inventory/company repositories that still rely on raw JDBC or string-built SQL
2. extend DTO and Bean Validation coverage across the remaining legacy controllers
3. convert the Flyway scaffold into curated migrations for shared tables and controlled tenant provisioning changes
4. complete structured logging replacement in the remaining high-risk financial and stock-changing paths

That sequencing keeps the roadmap aligned with business correctness first: finish rollback safety, validation, and repository consistency across the remaining legacy write surface before moving on to accounting and reporting expansion.
