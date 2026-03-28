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

Start Phase 2 with service-layer extraction and transaction management for the most critical write flows first:

1. order creation
2. inventory mutation
3. supplier receipt and payment handling
4. expenses
5. payment and subscription side effects

That sequencing preserves the roadmap priority: stabilize business correctness before expanding accounting and reporting features.
