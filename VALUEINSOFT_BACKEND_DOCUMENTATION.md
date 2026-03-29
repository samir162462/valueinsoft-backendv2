# Valueinsoft Backend Documentation

## 1. Overview

`valueinsoft-backendv2` is the Spring Boot backend that powers the Valueinsoft web application. It provides:

- authentication and JWT issuance,
- user, company, and branch management,
- POS product and order APIs,
- inventory, damaged items, and supplier operations,
- client and receipt flows,
- expenses and finance endpoints,
- dashboard and analytics data,
- branch subscription tracking,
- and online payment integration through PayMob.

The backend is functionally a multi-tenant business platform API. Its most important architectural trait is that tenant data is split across PostgreSQL schemas named like `C_<companyId>`, with many branch-specific tables named per branch, for example:

- `C_<companyId>."PosProduct_<branchId>"`
- `C_<companyId>."PosOrder_<branchId>"`
- `C_<companyId>."PosOrderDetail_<branchId>"`
- `C_<companyId>."supplier_<branchId>"`
- `C_<companyId>."InventoryTransactions_<branchId>"`

This gives each company an isolated schema while still using one application codebase and one PostgreSQL database.

## 2. Technology Stack

### Core platform

- Spring Boot `2.5.4`
- Java `17`
- Maven build
- Spring Web
- Spring Security
- Spring JDBC
- Spring AOP
- PostgreSQL
- Lombok
- Gson
- JJWT (`io.jsonwebtoken`)

### Packaging style

The codebase is organized by package responsibility rather than strict domain modules:

| Package | Purpose |
| --- | --- |
| `Config` | Spring profiles and datasource setup |
| `Controller` | Main REST controllers |
| `Controller.posController` | POS, product, order, shift, category, and fix-area endpoints |
| `Controller.MoneyController` | Receipts and expense endpoints |
| `Controller.DataVisualizationControllers` | Dashboard and analytics endpoints |
| `Controller.MainAppController` | Subscription-related endpoints |
| `DatabaseRequests` | Repository-style database access classes |
| `DatabaseRequests.DbPOS` | POS and inventory data access |
| `DatabaseRequests.DbMoney` | Client and supplier receipt persistence |
| `DatabaseRequests.DbApp` | Subscription persistence |
| `Filters` | JWT request filter |
| `SecurityPack` | Security configuration and user details service |
| `Model` | Domain DTOs and response models |
| `OnlinePayment` | PayMob integration |
| `Service` | Thin service layer, currently most visible around products |
| `SqlConnection` | Legacy raw JDBC connection utility and DB export file |
| `util` | JWT utility, pagination helper, timestamp conversion, misc helpers |

## 3. Build and Runtime Configuration

### Maven configuration

The `pom.xml` shows a straightforward Spring Boot application with:

- executable Spring Boot Maven plugin,
- Java 17 compiler configuration,
- provided-scope Lombok,
- default Spring Boot test dependency.

### Application properties

`src/main/resources/application.properties` currently contains:

- active profile set to `dev`,
- a scheduled idle-termination delay,
- a PayMob auth token value.

### Datasource configuration

There are two explicit datasource configs:

- `ConnectionConfigDev`
- `ConnectionProduction`

Both currently point to the same local PostgreSQL database:

- URL: `jdbc:postgresql://localhost:5432/localvls`
- username: `postgres`
- password: `0000`

This means the production profile is not yet truly production-specific.

### Legacy connection utility

`SqlConnection/ConnectionPostgres.java` still exists and is used heavily by older repository code. It switches between:

- local database
- and an EC2-hosted PostgreSQL endpoint

based on the `ValueinsoftBackendApplication.goOnline` flag.

So the backend currently mixes:

- Spring-managed `JdbcTemplate` access,
- `NamedParameterJdbcTemplate`,
- and manual `DriverManager` + `Connection` usage.

## 4. Application Entry Point

`ValueinsoftBackendApplication.java` is the main Spring Boot class.

Important responsibilities visible there:

- starts the Spring Boot app,
- preloads all branches into the static `branchArrayList`,
- stores global runtime constants like `trustedHost`, `DatabaseOwner`, `PAYMTOKEN`, and `goOnline`,
- schedules database idle-process termination through `DbSqlCloseIdles`,
- contains a placeholder scheduled task.

This class holds more runtime configuration than a typical Spring Boot entry point, so it acts as both launcher and part of the application's global state.

## 5. Security and Authentication

### Authentication flow

The backend authenticates users through `AuthenticateController`:

- endpoint: `POST /authenticate`
- authenticates with Spring Security `AuthenticationManager`
- loads the user through `MyUserDetailsServices`
- creates a JWT using `JwtUtil`
- returns a response containing JWT, username, and role

### UserDetails loading

`MyUserDetailsServices`:

- loads users from `DbUsers`,
- converts the application's role string into Spring Security authorities,
- encodes the username inside the security subject as `"username : role"`.

### JWT utility

`JwtUtil`:

- signs tokens with a static secret key `ValueInSoft_secret`,
- issues tokens valid for 72 hours,
- extracts claims and validates expiration.

### Request filtering

`JwtRequestFilter`:

- reads the `Authorization: Bearer <token>` header,
- extracts username from the JWT,
- reloads the user via `MyUserDetailsServices`,
- sets Spring Security authentication into the context.

### Security configuration

`SecurityConfiguration` currently:

- disables CSRF,
- sets stateless sessions,
- permits `/authenticate`,
- permits `/users/saveNewUser`,
- permits some company and user existence-check endpoints,
- restricts `/users/saveUser` to `Admin` and `Owner`,
- requires authentication for all other requests,
- adds the JWT filter before `UsernamePasswordAuthenticationFilter`,
- requires HTTPS when `X-Forwarded-Proto` is present.

### CORS behavior

CORS is defined in two places:

- `WebConfig` allows only `https://dierlo.com`
- `DevConfiguration` allows everything, but it is annotated with profile `development`

Because `application.properties` activates profile `dev`, the permissive dev config does not appear to match the active profile name. That means current runtime behavior may not align with intended local-development CORS behavior.

## 6. Database Architecture

### Multi-tenant design

The backend uses a hybrid schema strategy:

- global tables in `public` for shared platform records such as users, companies, branches, and subscriptions,
- per-company schemas like `C_1076`,
- per-branch tables inside those schemas.

This pattern is visible in repository code and in the exported database file under `SqlConnection/VLSBackV2.sql`.

### Schema lifecycle

When a company is created:

- `DbCompany.AddCompany(...)` inserts the company into `public."Company"`
- `DbCompany.CreateCompanySchema(companyId)` creates the tenant schema
- the owner role is promoted to `Owner`
- an initial branch may be created

When a branch is created:

- `DbBranch.addBranch(...)` inserts the branch into `public."Branch"`
- branch-specific tables are created, including product, order, order detail, supplier, and inventory transaction tables

### Persistence styles

The codebase currently has two repository generations:

#### Newer style

- constructor-injected dependencies
- `JdbcTemplate` and `NamedParameterJdbcTemplate`
- cleaner mapping logic
- safer parameter binding

Examples:

- `DbUsers`
- `DbClient` v2
- `DbBranch`
- `DbPosProduct`
- `DbPosProductCommandRepository`

#### Older style

- static methods
- manual `Connection`, `Statement`, `PreparedStatement`
- string-built SQL
- dynamic SQL table names

Examples:

- `DbCompany`
- `DbPosOrder`
- `DbSupplier`
- `DbExpenses`
- `DbSubscription`
- several money and analytics repositories

The repository layer is therefore partially modernized, but not yet consistent.

## 7. Main Domain Models

The `Model` package shows the backend's business vocabulary:

- `User`
- `Company`
- `Branch`
- `Client`
- `Supplier`
- `SupplierBProduct`
- `Product`
- `ProductFilter`
- `Order`
- `OrderDetails`
- `InventoryTransaction`
- `DamagedItem`
- `Expenses`
- `ShiftPeriod`
- `SlotsFixArea`
- `MainMajor`
- `Category`
- `SubCategory`
- `ClientReceipt`
- `SupplierReceipt`
- dashboard models under `Model.DataVisualizationModels`
- subscription models under `Model.AppModel`
- response DTOs under `Model.ResponseModel`

These models are mostly request/response carriers rather than JPA entities, because the application is not using Spring Data JPA.

## 8. API Surface by Functional Area

### 8.1 Authentication

| Endpoint | Purpose |
| --- | --- |
| `POST /authenticate` | Login and JWT issuance |

### 8.2 Users

Handled by `UserController`.

Main operations:

- `GET /users/getUser`
- `GET /users/getUserDetails/{userName}`
- `GET /users/{companyId}/{branchId}/getAllUsers`
- `GET /users/checkUserEmail/{Email}`
- `GET /users/checkUserUserName/{UserName}`
- `GET /users/getUserImg`
- `POST /users/saveNewUser`
- `POST /users/saveUser`
- `PUT /users/resetPassword/{userName}`
- `PUT /users/updateImg/{userName}`

These endpoints support both public registration and authenticated branch user management.

### 8.3 Companies and branches

Handled by `CompanyController` and `BranchController`.

Company endpoints:

- `GET /Company/getCompany`
- `GET /Company/getAllCompanies`
- `GET /Company/getCompanyAndBranchesByUserName`
- `GET /Company/getCompanyById`
- `POST /Company/saveCompany`
- `PUT /Company/updateImg/{companyId}`

Branch endpoints:

- `GET /Branch/getBranchById`
- `GET /Branch/{id}/getBranchesByCompanyId`
- `POST /Branch/AddBranch`
- `GET /Branch/isActive/{branchId}`

These are core multi-tenant provisioning APIs.

### 8.4 Clients

Handled by `ClientController`.

Main operations:

- lookup by phone
- lookup by name
- latest clients by branch
- specific client by ID
- current-year client counts
- create client

The client repository has already been refactored into a cleaner `JdbcTemplate`-based implementation compared to older repository classes.

### 8.5 Suppliers

Handled by `SupplierController`.

Main operations:

- list suppliers per company/branch
- save supplier
- update supplier
- delete supplier
- supplier sales history
- supplier purchased products
- save supplier bought-product relation
- remaining supplier amount by product

This module still relies on a large amount of older static SQL logic.

### 8.6 Products and inventory

Handled through:

- `ProductController`
- `InventoryTransactionController`
- `DamagedItemController`
- `CategoryController`

#### Product APIs

- search by direct text
- search by company/category name
- search by barcode
- filtered search with pagination
- fetch product by ID
- create product
- edit product
- autocomplete product names

This is one of the most modernized areas in the backend. It uses:

- `ProductService`
- `DbPosProduct`
- `DbPosProductCommandRepository`
- `ProductQueryBuilder`

and introduces:

- reusable query composition,
- safer identifier validation for branch/table names,
- pagination response objects,
- separate read/write responsibilities.

#### Inventory transaction APIs

- `POST /invTrans/AddTransaction`
- `POST /invTrans/transactions`

These record stock-affecting financial movements and retrieve transaction history over a date range.

#### Damaged item APIs

- list damaged items
- add damaged item
- delete damaged item

There is also an update route, but the current controller method still points to supplier update logic, which indicates unfinished or incorrect wiring.

#### Category APIs

- save category JSON
- load category JSON
- load flat category JSON
- load main majors

Category data is stored as JSON and also supports major-category retrieval per company.

### 8.7 Orders, shifts, and repair/fix area

#### Orders

Handled by `OrderController` and `DbPosOrder`.

Main operations:

- save order
- get orders by client
- get order details
- bounce back a sold item to inventory or supplier path

`DbPosOrder` performs several business-side effects:

- inserts the order header,
- inserts order detail rows,
- reduces product quantity,
- calculates or updates bounced-back totals and income.

This is an important transactional area, but it is still implemented with raw SQL string construction.

#### Shift periods

Handled by `ShiftPeriodController`.

Main operations:

- start shift
- end shift
- get current shift
- get orders by shift
- get branch shifts

This supports the frontend's shift-based POS reporting and closing workflow.

#### Fix area

Handled by `SlotsFixAreaController`.

Main operations:

- list fix-area slots by month
- add slot
- update slot

This is the backend for workshop/service-repair activity exposed in the frontend as "Fix Area".

### 8.8 Money and finance

Handled under `Controller.MoneyController`.

#### Client receipts

`ClientReceiptController` supports:

- client receipts by client ID
- receipts by branch/time period
- create new client receipt

#### Supplier receipts

`SupplierReceiptController` supports:

- retrieve supplier receipts
- add supplier receipt

#### Expenses

Expenses are split into:

- `ExpensesController`
- `ExpensesStaticController`

Both implement a shared `Crud` interface that exposes:

- `getAll`
- `getById`
- `create`
- `updateById`
- `DeleteById`

However, implementation completeness differs:

- dynamic purchase-style expenses are implemented
- static expenses are partly implemented
- delete and some update paths are missing or return `null`

This controller interface creates consistent endpoints, but the underlying module is still incomplete.

### 8.9 Dashboards and analytics

Analytics endpoints are grouped under:

- `DvSalesController` at `/Dv`
- `DVSalesCompanyController` at `/DvCompany`
- `DVCompanyAnalysisController` at `/DvCa`

Capabilities include:

- monthly sales
- yearly sales
- sales products by period
- company-wide sales
- per-day company sales
- company analysis record creation and incremental updates

The analytics layer is tightly coupled to database tables in each company schema.

### 8.10 Subscription management

`AppSubscriptionController` and `DbSubscription` handle branch subscription lifecycle:

- get branch subscriptions
- add subscription
- inspect callback response parameters
- mark subscription success after payment callback
- determine whether a branch is active

This is what supports the frontend's branch activation checks and subscription/payment screens.

### 8.11 Online payments

`PayMobController` provides direct integration with PayMob:

- authenticate against PayMob
- request payment key token
- construct iframe URL for card payment
- receive transaction processed callback (`/OP/TPC`)

The flow is:

1. create PayMob auth token,
2. create payment key request,
3. return hosted iframe URL,
4. receive callback,
5. update subscription status to paid.

This is a business-critical integration, but it currently contains hard-coded values and static/global state.

## 9. Services and Utility Layer

### Product service

`ProductService` is the clearest example of a proper service layer. It:

- delegates search and command operations,
- adds fallback behavior for product search,
- standardizes operation responses for create/update operations.

### Utilities

Important utilities include:

- `JwtUtil`
- `PageHandler`
- `ConvertStringToTimeStamp`
- `CustomPair`
- `EncriptionByNum`

These support cross-cutting concerns like auth, pagination, parsing, and response shaping.

## 10. Testing Status

The test tree is currently very small:

- `ValueinsoftBackendApplicationTests` with only `contextLoads()`
- an empty `ExpensesControllerTest`
- one file under `ScurityServices`

So the backend has test scaffolding, but not meaningful automated coverage of business behavior, security rules, repository queries, or controller contracts.

## 11. Current Architectural Strengths

- The backend covers a broad operational domain in one codebase.
- The company-per-schema design gives a clear tenant isolation strategy.
- Product search and write paths show a newer, cleaner repository/service approach.
- JWT authentication is integrated across the API.
- Branch subscriptions and PayMob are tied into the business lifecycle.
- The codebase preserves domain-specific business workflows such as shifts, bounce-backs, damaged items, supplier receipts, and client receipts.

## 12. Current Gaps and Technical Risks

- Sensitive values and database credentials are hard-coded in source and properties.
- Security uses `NoOpPasswordEncoder`, meaning passwords are effectively stored without hashing.
- Repository style is inconsistent: modern Spring JDBC exists beside raw `Connection` and string-built SQL.
- Several older repositories build SQL dynamically in ways that increase maintenance and injection risk.
- Global mutable state exists in `ValueinsoftBackendApplication`.
- CORS/profile configuration is inconsistent (`dev` vs `development`).
- Production datasource configuration currently mirrors development settings.
- Some controllers or routes are incomplete or wrongly wired, such as damaged-item update behavior and the empty `WebAdminController`.
- The `target/` build output is present and currently tracked/dirty in the repo.
- Automated tests are minimal.
- Transaction management is largely manual in high-risk areas like order creation.
- Error handling is inconsistent and often reduced to `System.out.println(...)`.

## 13. Recommended Enhancements

### Security

- Replace `NoOpPasswordEncoder` with `BCryptPasswordEncoder`.
- Move JWT secret, database credentials, and PayMob keys into environment variables or secure secrets management.
- Review and reduce the number of public endpoints.
- Add role-based authorization beyond the current limited `saveUser` restriction.
- Standardize CORS configuration per environment and fix the current profile mismatch.

### Architecture

- Complete the migration from raw JDBC/static methods to constructor-injected Spring repositories and services.
- Introduce transaction boundaries using Spring `@Transactional` in order creation, inventory updates, subscription writes, and payment callback handling.
- Separate controller DTOs from internal domain models where business rules are complex.
- Reduce global static state in `ValueinsoftBackendApplication`.
- Move tenant schema and table naming logic into a dedicated infrastructure component.

### Database and persistence

- Replace string-concatenated SQL with parameterized queries everywhere possible.
- Add schema migration tooling such as Flyway or Liquibase.
- Normalize dynamic schema/table creation into migration scripts or managed provisioning services.
- Review indexing and constraints for company and branch scoped tables.
- Decide whether the long-term strategy is per-schema multitenancy or a simpler shared-schema model with tenant keys.

### Reliability

- Add structured logging instead of `System.out.println`.
- Return consistent JSON error responses with status codes and error codes.
- Add validation at controller boundaries for required fields and invalid types.
- Add idempotency or replay protection for payment callback handling.
- Expand health checks and operational observability.

### Code quality

- Remove or archive commented-out legacy implementations once replacements are stable.
- Fix incorrectly wired endpoints such as damaged-item update handling.
- Remove or implement placeholder controllers such as `WebAdminController`.
- Clean naming inconsistencies and spelling issues like `ScurityServices`.
- Keep generated `target/` artifacts out of version control.

### Testing

- Add repository tests for users, companies, branches, clients, products, orders, expenses, and subscriptions.
- Add controller integration tests for authentication, protected routes, and payment callback flows.
- Add regression tests for multi-tenant schema provisioning when a company or branch is created.
- Add security tests to verify JWT authentication and authorization rules.

### Product evolution

- Expand service-layer orchestration so business rules are not buried inside controllers or SQL builders.
- Formalize the payment/subscription workflow with explicit states and audit trails.
- Add API documentation through OpenAPI or SpringDoc.
- Add versioned API contracts if frontend and backend will continue evolving independently.

## 14. Backend Modernization Roadmap

A dedicated modernization roadmap now lives in `VALUEINSOFT_BACKEND_MODERNIZATION_PLAN.md`.

That roadmap is intentionally phased so the backend can be evolved safely without a rewrite. It follows this order:

1. Phase 1: essential security and stability
2. Phase 2: service-layer and transaction foundation
3. Phase 3: business completeness for sales, supplier accounting, inventory, and shifts
4. Phase 4: operational accounting ledger
5. Phase 5: reporting
6. Phase 6: tests, logging, and stronger operational practice

## 15. Phase 1 Essential Status Update

Status on 2026-03-29:

- completed for the planned Phase 1 scope
- verified with `./mvnw.cmd -q -DskipTests compile`

Implemented in Phase 1:

- replaced `NoOpPasswordEncoder` with a legacy-aware BCrypt migration path
- externalized datasource, JWT, PayMob, and CORS configuration into Spring property classes and environment-backed settings
- standardized profile handling to `dev` and `production`
- centralized CORS in Spring Security and removed controller-level wildcard CORS annotations
- added a global structured API error response model and exception handler
- refactored the touched auth and finance repositories toward validated parameterized SQL
- added a shared tenant SQL identifier validator for dynamic schema and table names

What remains for the next phase:

- service-layer extraction across the main domains
- transaction management for order creation, inventory updates, supplier payments, and expenses
- request DTO validation
- migration tooling
- broader repository cleanup beyond the touched Phase 1 modules

## 16. Phase 2 Foundation Status Update

Status on 2026-03-29:

- started for the backend foundation phase
- completed for the order slice and the next finance/subscription write slice
- verified with `.\mvnw.cmd -q -DskipTests compile`

Implemented so far in Phase 2:

- added `OrderService` so order creation and order-item bounce-back no longer live directly in the controller path
- added Spring transaction management for the touched order write path
- refactored `DbPosOrder` away from the old string-built multi-statement order save flow into validated parameterized Spring JDBC operations
- expanded `TenantSqlIdentifiers` so the order module uses centralized validated dynamic schema/table naming
- added request DTO validation for authentication, order save, order bounce-back, user creation, and password reset
- extended the global exception handler so validation failures return structured field-level details
- reduced JWT stale-token noise to debug level in the request filter
- added `target/` to `.gitignore` so new build output is ignored by default
- added `SupplierReceiptService` and moved supplier-receipt creation plus related inventory/supplier balance updates behind one transaction boundary
- moved the touched supplier receipt persistence path to parameterized Spring JDBC operations with validated tenant identifiers
- added `ExpensesService` and validated DTOs for create and update operations in both expenses controllers
- added `SubscriptionService` and `PayMobService` so subscription creation, PayMob order/payment-key generation, callback parsing, and payment-success updates no longer rely on controller-owned static logic
- refactored `DbSubscription` into constructor-injected Spring JDBC code
- added validated DTOs for supplier receipts, expenses, branch subscriptions, and PayMob payment-token requests
- added Flyway scaffolding with a baseline placeholder migration while leaving Flyway disabled by default until curated migrations are ready
- removed already tracked `target/` build output from the git index so the next commit can leave the repository source-only while keeping local build files on disk
- added `SupplierService` so supplier create/update/delete and supplier bought-product writes no longer depend on static repository access
- refactored `DbSupplier` to parameterized Spring JDBC with validated tenant identifiers
- added `InventoryTransactionService` and refactored `DbPosInventoryTransaction` away from string-built multi-statement SQL into service-owned transactional writes
- added validated DTOs for supplier create/update, supplier bought-product, inventory transaction create, and inventory transaction query payloads

Current result:

- the backend now has a real transactional service boundary for the most critical POS write flow
- order creation and bounce-back are safer against partial-write failure than the old controller/raw-SQL path
- supplier receipts, expenses, and branch-subscription payment side effects now also run through explicit service boundaries instead of controller-owned orchestration
- supplier master-data writes and inventory-transaction writes now also run through typed controller payloads and service-owned transaction boundaries
- the touched money and payment endpoints reject invalid payloads early instead of failing later inside repository code
- Flyway structure and repository hygiene are prepared for the next foundation pass
- and the current roadmap risk is narrowed to company provisioning, damaged-item flows, analytics updates, and the remaining legacy-controller areas

Remaining Foundation work:

- service extraction and transaction boundaries in the untouched money and stock paths that still rely on legacy raw JDBC patterns
- request DTO migration across the remaining controllers that still use loose maps
- curated Flyway migrations for shared tables and controlled tenant-provisioning changes
- broader repository cleanup in untouched company, damaged-item, and analytics modules
- structured logging completion across the remaining legacy write surface
