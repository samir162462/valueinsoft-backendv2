# VALUEINSOFT SUBSCRIPTION AND BILLING TARGET OPERATING MODEL

Status:
- Planning only

Date:
- 2026-04-06

Purpose:
- assess the current subscription and payment implementation
- define a production-grade subscription and monthly billing model for multi-branch tenants
- propose a mock payment provider path so subscription flows can be completed safely before real gateway rollout

Interpretation rules:
- `Existing` means verified in the current backend code
- `Inferred` means directly derived from verified code shape
- `Proposed` means recommended future implementation

---

## 1. Executive Summary

The current system has a usable branch-subscription starting point, but it is not yet a market-standard recurring billing platform.

Today the system:
- stores subscription rows in `public."CompanySubscription"`
- creates a payment order through PayMob during subscription creation
- marks the latest subscription as paid through a payment callback
- reports platform billing mostly as "latest known subscription row per branch"

This is enough for basic branch payment tracking, but not enough for a reliable SaaS recurring billing lifecycle.

The strongest design direction for ValueInSoft is:
- keep the tenant/company as the commercial account owner
- treat each branch as a billable subscription unit
- invoice and collect at the tenant billing-account level
- keep branch-level entitlement and branch-level lifecycle decisions in the product runtime

This means:
- one tenant billing account
- one or more branch subscriptions under that account
- one recurring invoice cycle that can aggregate all billable branches
- branch entitlements controlled by subscription state, grace policy, and lifecycle policy

For now, the next safe step is not immediate gateway expansion. The next safe step is:
- formalize the billing domain model
- add a `MockPaymentGateway`
- implement invoice-cycle logic and subscription state transitions
- then keep PayMob behind a provider abstraction

---

## 2. Current Verified Implementation

### 2.1 Existing Backend Entry Points

Verified files:
- `src/main/java/com/example/valueinsoftbackend/Controller/MainAppController/AppSubscriptionController.java`
- `src/main/java/com/example/valueinsoftbackend/OnlinePayment/OPController/PayMobController.java`
- `src/main/java/com/example/valueinsoftbackend/Service/SubscriptionService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/PayMobService.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbApp/DbSubscription.java`

Existing subscription endpoints:
- `GET /appSubscription/{branchId}`
- `POST /appSubscription/AddSubscription`
- `GET /appSubscription/Res`
- `POST /OP/paymentTKNRequest`
- `POST /OP/TPC`

### 2.2 Existing Subscription Flow

Verified current behavior:
1. an operator creates a branch subscription using `CreateSubscriptionRequest`
2. `SubscriptionService.addBranchSubscription(...)` inserts a row into `public."CompanySubscription"`
3. the same flow immediately creates a PayMob order through `PayMobService.createPayMobOrder(...)`
4. the PayMob order id is written back into the same subscription row
5. `POST /OP/TPC` receives a payment callback
6. on `success=true`, `SubscriptionService.markBranchSubscriptionStatusSuccess(...)` marks the row as paid

### 2.3 Existing Data Model

Verified current persisted shape from code:
- table: `public."CompanySubscription"`
- fields used by the code:
  - `"sId"`
  - `"startTime"`
  - `"endTime"`
  - `"branchId"`
  - `"amountToPay"`
  - `"amountPaid"`
  - `order_id`
  - `status`

Verified current status usage:
- `NP`
- `PD`

Inferred current meaning:
- `NP` = not paid
- `PD` = paid

### 2.4 Existing Reporting Model

Verified files:
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminBillingReadModels.java`
- `src/main/java/com/example/valueinsoftbackend/DatabaseRequests/DbPlatformAdminDailyMetrics.java`

Verified reporting behavior:
- platform billing reads `DISTINCT ON (branchId)` to treat the latest subscription row as the current state for each branch
- unpaid subscription counts are derived from latest row status per branch
- revenue and outstanding values are computed from latest subscription rows, not from invoices, payment attempts, or ledger entries

### 2.5 Existing Onboarding And Branch Provisioning

Verified files:
- `src/main/java/com/example/valueinsoftbackend/Service/CompanyService.java`
- `src/main/java/com/example/valueinsoftbackend/Service/BranchService.java`

Verified current behavior:
- company creation may create an initial branch
- branch creation provisions branch runtime tables
- branch creation does not create a subscription automatically
- onboarding is not currently subscription-driven

### 2.6 Existing Security And Operational Constraints

Verified files:
- `src/main/java/com/example/valueinsoftbackend/SecurityPack/SecurityConfiguration.java`
- `src/main/java/com/example/valueinsoftbackend/OnlinePayment/PayMobProperties.java`

Verified behavior:
- `/OP/TPC` is public
- `/appSubscription/Res` is public
- PayMob secret presence is only validated at runtime when requests are made

Verified technical limitations:
- no provider abstraction exists
- `SubscriptionService` is directly coupled to `PayMobService`
- callback naming is confusing: `markBranchSubscriptionStatusSuccess(int orderId)` receives a value called `subId` in callback handling, even though it is effectively a payment order identifier

---

## 3. Current Gaps Versus Market-Standard SaaS Billing

### 3.1 What Exists

Existing:
- branch-level subscription rows
- gateway order creation
- payment-key URL generation
- payment success callback handling
- basic platform billing summary
- branch active check based on latest subscription row

### 3.2 What Is Missing

Missing in the current system:
- tenant billing account model
- customer billing profile
- saved payment method model
- invoice entity
- invoice line items
- payment attempt entity
- webhook event log
- idempotency tracking
- retry and dunning workflow
- grace period policy
- trial state
- pending activation state
- past-due state
- canceled state
- paused state
- failed payment recovery workflow
- scheduled renewal job
- proration and scheduled plan changes
- branch entitlement policy driven by subscription lifecycle
- provider abstraction
- mock payment provider for safe non-live testing
- reconciliation job
- finance-grade audit trail for billing changes

### 3.3 Market-Standard Capabilities Not Yet In System

What mature subscription systems typically support, but ValueInSoft does not yet have:
- subscription lifecycle states beyond paid or unpaid
- invoice-first billing instead of row-first billing
- retry and dunning flows for failed recurring payments
- webhook signature verification and idempotent processing
- customer-level payment instruments and update flows
- scheduled changes, trial handling, pause or cancel at period end
- clear separation between:
  - subscription contract
  - invoice
  - payment attempt
  - entitlement state

Official references:
- Stripe subscription lifecycle: https://docs.stripe.com/subscriptions/lifecycle
- Stripe webhook best practices: https://docs.stripe.com/webhooks
- Paddle subscription overview: https://developer.paddle.com/api-reference/subscriptions/overview

---

## 4. Best-Practice Commercial Model For ValueInSoft

### 4.1 Recommended Commercial Owner

Proposed:
- the tenant/company is the billing customer
- branches are billable units under that tenant

Reason:
- tenants already own plans, templates, configuration, and operations
- branches are operational entities, not independent commercial customers
- aggregated billing at tenant level is simpler, more standard, and easier to recover when payments fail

### 4.2 Recommended Subscription Structure

Proposed:
- `BillingAccount`
  - one per tenant
- `BranchSubscription`
  - one current active contract per billable branch
- `Invoice`
  - one invoice per billing cycle for the tenant, optionally with multiple branch line items
- `PaymentAttempt`
  - each charge or collection attempt against an invoice

Recommended rule:
- branch is the entitlement unit
- tenant is the payer

### 4.3 Recommended Entitlement Rule

Proposed:
- branch access should depend on the branch subscription entitlement state
- tenant-wide suspension should be reserved for severe account-level cases, not every missed branch payment

Recommended lifecycle effect:
- active or trial branch subscription -> branch stays operational
- past_due within grace period -> branch stays operational with alerts
- grace expired and still unpaid -> branch becomes locked
- tenant-level manual override remains possible from platform admin

---

## 5. Recommended Lifecycle From Onboarding To Monthly Renewal

### 5.1 Onboarding Lifecycle

Proposed onboarding sequence:
1. create tenant
2. create billing account for tenant
3. create first branch
4. create branch subscription in `pending_activation` or `trialing`
5. create initial invoice for the tenant
6. collect payment through provider or mock provider
7. activate branch entitlement when the invoice is settled
8. mark onboarding complete only after the commercial requirement is satisfied, or explicitly mark onboarding as trial mode

### 5.2 Adding Additional Branches

Proposed branch-addition sequence:
1. branch is created
2. branch subscription is created in `pending_activation`
3. branch line item is added to:
   - either an immediate proration invoice
   - or the next scheduled invoice
4. branch remains:
   - trialing
   - pending activation
   - or temporarily active under a controlled grace policy
5. branch entitlement is finalized when billing state becomes valid

### 5.3 Monthly Renewal Lifecycle

Proposed recurring sequence:
1. scheduler identifies tenant billing accounts due for renewal
2. system builds invoice from all active billable branches
3. invoice line items are created per branch
4. payment attempt is created
5. provider charge is initiated
6. webhook or mock callback settles the payment attempt
7. invoice becomes `paid` or `past_due`
8. branch subscription entitlements are updated
9. platform alerts and support views update automatically

### 5.4 Failed Payment Lifecycle

Proposed:
1. payment fails
2. invoice becomes `past_due`
3. system starts retry schedule
4. support and alert workflows surface the account
5. grace window runs
6. if still unpaid after grace:
   - affected branches lock
   - tenant billing account becomes delinquent
7. once recovered:
   - invoice is paid
   - branches unlock automatically or by policy

---

## 6. Proposed Target State Model

### 6.1 Subscription State Model

Proposed branch subscription states:
- `draft`
- `pending_activation`
- `trialing`
- `active`
- `past_due`
- `paused`
- `canceled`
- `expired`

### 6.2 Invoice State Model

Proposed invoice states:
- `draft`
- `open`
- `payment_pending`
- `paid`
- `past_due`
- `void`
- `uncollectible`

### 6.3 Payment Attempt State Model

Proposed payment attempt states:
- `created`
- `redirect_required`
- `processing`
- `succeeded`
- `failed`
- `canceled`
- `reconciled`

### 6.4 Branch Runtime Mapping

Proposed entitlement mapping:
- `active` and `trialing` -> branch active
- `past_due` with grace -> branch active but alerting
- `past_due` without grace -> branch locked
- `canceled` or `expired` -> branch locked

---

## 7. Proposed Domain Design

### 7.1 Keep

Keep:
- `public."CompanySubscription"` as legacy source during migration
- `SubscriptionService` as the current orchestration point until a new billing domain is introduced
- `PlatformAdmin` billing read models as interim reporting views

### 7.2 Extend

Extend:
- platform-admin billing reads to use invoice and payment tables once available
- branch lifecycle logic to consume entitlement state
- onboarding state to recognize subscription readiness

### 7.3 Replace Gradually

Replace gradually:
- direct `SubscriptionService -> PayMobService` coupling
- current "latest subscription row = source of truth" assumption
- payment success reconciliation based only on the gateway order id in one row

### 7.4 Proposed New Service Split

Proposed services:
- `BillingAccountService`
- `BranchSubscriptionService`
- `InvoiceService`
- `PaymentAttemptService`
- `BillingEntitlementService`
- `BillingSchedulerService`
- `PaymentProvider` interface
- `MockPaymentProvider`
- `PayMobPaymentProvider`

---

## 8. Proposed Mock Payment Service

### 8.1 Why It Is Needed

Proposed reason:
- production billing flows should be completed and tested before depending on live gateway behavior
- the frontend, onboarding, support, and alert workflows need a deterministic payment environment
- mock mode reduces risk while lifecycle rules are still evolving

### 8.2 Recommended Design

Proposed provider abstraction:
- `PaymentProvider`
  - `createCheckout(...)`
  - `captureOrInitiate(...)`
  - `parseCallback(...)`
  - `verifyCallback(...)`
  - `getProviderName()`

Proposed implementations:
- `MockPaymentProvider`
- `PayMobPaymentProvider`

Proposed runtime selection:
- `vls.billing.provider=mock|paymob`

### 8.3 Mock Provider Behavior

Proposed mock capabilities:
- create deterministic fake checkout URLs
- simulate:
  - success
  - failure
  - pending
  - timeout
- support test scenarios:
  - first payment success
  - renewal success
  - renewal failure
  - recovery payment
  - expired grace period

### 8.4 Mock Provider Endpoints

Proposed mock-only endpoints:
- `POST /billing/mock/checkout`
- `POST /billing/mock/payments/{attemptId}/succeed`
- `POST /billing/mock/payments/{attemptId}/fail`
- `POST /billing/mock/payments/{attemptId}/mark-pending`

These are planning-only recommendations, not current endpoints.

---

## 9. Proposed Data Model Plan

### 9.1 Existing To Preserve During Migration

Existing:
- `public."CompanySubscription"`
- `public."Branch"`
- `public."Company"`
- `public.tenants`
- `public.package_plans`

### 9.2 Proposed New Tables

Proposed:
- `public.billing_accounts`
  - tenant-level payer profile
- `public.branch_subscriptions`
  - branch commercial contract and entitlement state
- `public.billing_prices`
  - normalized recurring prices and billing interval
- `public.billing_invoices`
  - invoice header per tenant billing cycle
- `public.billing_invoice_lines`
  - branch-level invoice lines
- `public.billing_payment_attempts`
  - gateway or mock collection attempts
- `public.billing_provider_events`
  - raw webhook or callback log with idempotency key
- `public.billing_payment_methods`
  - future-ready customer payment method reference
- `public.billing_dunning_runs`
  - retry and reminder schedule tracking
- `public.billing_entitlement_events`
  - audit trail for branch entitlement changes

### 9.3 Proposed Migration Strategy

Proposed:
1. keep writing current flow until new model exists
2. introduce new tables without breaking reads
3. dual-write new subscription creations into:
   - legacy `CompanySubscription`
   - new billing tables
4. switch platform-admin billing reads to new tables
5. switch branch active checks to entitlement service
6. retire legacy-only logic after validation

---

## 10. Proposed API Plan

### 10.1 Onboarding And Branch Provisioning

Proposed:
- `POST /api/billing/tenants/{tenantId}/subscriptions/initialize`
- `POST /api/billing/branches/{branchId}/subscriptions`
- `POST /api/billing/branches/{branchId}/activate-trial`

### 10.2 Billing Operations

Proposed:
- `GET /api/billing/tenants/{tenantId}/account`
- `GET /api/billing/tenants/{tenantId}/subscriptions`
- `GET /api/billing/tenants/{tenantId}/invoices`
- `GET /api/billing/invoices/{invoiceId}`
- `POST /api/billing/invoices/{invoiceId}/collect`
- `POST /api/billing/invoices/{invoiceId}/void`

### 10.3 Provider And Callback

Proposed:
- `POST /api/billing/provider/checkout`
- `POST /api/billing/provider/callback`
- `POST /api/billing/mock/payments/{attemptId}/succeed`
- `POST /api/billing/mock/payments/{attemptId}/fail`

### 10.4 Entitlement

Proposed:
- `GET /api/billing/branches/{branchId}/entitlement`
- `POST /api/billing/branches/{branchId}/lock-from-billing`
- `POST /api/billing/branches/{branchId}/unlock-from-billing`

---

## 11. Best-Practice Decisions Recommended For ValueInSoft

### 11.1 Invoice-First, Not Subscription-Row-First

Recommended:
- subscription is the contract
- invoice is the collectible event
- payment attempt is the processing event

This is better than the current model where one row tries to act as:
- contract
- invoice
- payment state
- entitlement marker

### 11.2 Aggregate Billing Per Tenant

Recommended:
- charge the tenant billing account
- itemize each active branch on the invoice

This is cleaner than making every branch a separate payer.

### 11.3 Provider Abstraction Before More Gateway Work

Recommended:
- do not deepen PayMob coupling
- introduce a provider interface first
- implement mock provider first
- then refactor PayMob behind the same interface

### 11.4 Idempotent Callback Processing

Recommended:
- every callback or webhook event must be persisted
- duplicate provider events must be ignored safely
- signature verification must be explicit

### 11.5 Branch Entitlement Must Be Explicit

Recommended:
- branch runtime state should not infer billing solely from "latest subscription row"
- use a dedicated entitlement decision service

---

## 12. What Mature Market Systems Usually Have

Typical market-standard billing stacks support:
- customer account and billing profile
- multiple plans and prices
- trials and free periods
- proration when seats or branches are added mid-cycle
- invoice issuance and invoice PDF or hosted payment page
- automatic recurring charge attempts
- retries and dunning
- card update or payment method recovery
- webhook signatures and replay-safe processing
- finance-grade audit trail
- clear separation of:
  - subscription
  - invoice
  - payment attempt
  - entitlement

ValueInSoft currently has only part of that stack:
- branch subscription storage
- gateway order creation
- payment token flow
- callback success marking
- platform reporting from latest branch subscription rows

---

## 13. Recommended Delivery Phases

### Phase 1 - Stabilize Current Flow

Recommended:
- document current legacy `CompanySubscription` semantics
- add provider abstraction
- add `MockPaymentProvider`
- normalize status vocabulary
- add internal billing audit logs

### Phase 2 - Introduce New Billing Core

Recommended:
- add billing account, branch subscription, invoice, invoice line, payment attempt, provider event tables
- dual-write new subscription creation
- implement invoice-first renewal flow

### Phase 3 - Entitlement And Collections

Recommended:
- add grace and retry policy
- add billing-driven branch locking and unlocking
- add dunning state and support workflows

### Phase 4 - Gateway Hardening

Recommended:
- refactor PayMob behind provider interface
- add callback verification and idempotency
- add reconciliation jobs

### Phase 5 - Platform And Customer Experience

Recommended:
- richer billing admin tools
- invoice and payment detail views
- customer payment method maintenance
- self-service billing if desired later

---

## 14. Recommended Immediate Next Step

The best immediate next step is:
- create a backend-only implementation spec for subscription and billing modernization

That implementation spec should cover:
- new data model
- provider abstraction
- mock payment provider
- onboarding integration
- renewal scheduler
- entitlement mapping
- migration from `CompanySubscription`

This should be done before further backend coding on billing.

---

## 15. Key Conclusions

- The current system is functional for basic branch payment tracking, but it is not yet a complete recurring billing platform.
- The correct commercial model is tenant-billed, branch-entitled.
- The system needs a mock payment provider before deeper gateway rollout.
- The system should move to invoice-first recurring billing with explicit entitlement state.
- The current reporting model based on "latest subscription row per branch" should be treated as interim only.
