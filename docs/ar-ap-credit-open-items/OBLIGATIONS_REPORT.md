# Payables & Receivables Report

For the complete business, operations, API, and troubleshooting manual, see `OPEN_ITEMS_HANDBOOK.md`.

The report is branch-scoped, read-only, and backed by authoritative operational subledgers.

## Tabs and sources

- **Should pay us**: client AR open items (`ar_open_item`).
- **We should pay**: supplier AP open items (`ap_open_item`), unpaid client trade-in receipts, and posted unpaid/partially-paid payroll run lines.
- Employee salary rows are returned only when the authenticated viewer also has `payroll.payment.read`.

Draft/reversed documents and settled balances are excluded. Amounts are grouped by currency and are never combined across currencies.

## Endpoints

- `GET /api/finance/reports/obligations`: paginated party summaries; requires `finance.report.read`.
- `GET /api/finance/reports/obligations/{side}/{partyId}`: open documents, order/purchase/payroll lines, and receipt/payment/note applications for one party and currency.

Both endpoints require `companyId`, `branchId`, and the report side (`RECEIVABLE` or `PAYABLE`). The list defaults to 50 and is capped at 200 parties.

## Frontend

The main aside has a dedicated **Reports** section containing:

- Payables & Receivables
- Financial reports

The obligations page provides direction tabs, as-of date, debounced search, currency totals, overdue counts, pagination, and an on-demand document-detail modal.
