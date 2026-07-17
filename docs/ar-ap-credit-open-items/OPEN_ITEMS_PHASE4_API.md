# Open Items Phase 4 API Contract

Phase 4 makes the server authoritative for settlement math. All monetary values use decimal amounts and all write endpoints require the branch-scoped capability documented in V145.

## Receipt creation changes

Client and supplier receipt requests accept these optional fields:

```json
{
  "currencyCode": "EGP",
  "idempotencyKey": "caller-stable-key",
  "allocations": [
    { "openItemId": 123, "amount": 50.0000 }
  ]
}
```

When `allocations` is absent or empty, the server allocates FIFO by `due_date`, then document date. Any remainder stays unallocated on the receipt. Supplier `remainingAmount` is accepted for one compatibility release but ignored; the server computes the authoritative remaining balance and derives legacy writes from it. Legacy writes can be disabled with `finance.openitems.legacy-writes.enabled=false`.

New client receipts and payouts must use positive amounts. A payout uses `type: "ClientPayout"`; historical negative receipt rows remain readable but new negative rows are rejected.

## Explicit allocation and reversal endpoints

- `POST /clientAccount/{companyId}/{branchId}/{clientId}/receipts/{receiptId}/allocations`
- `POST /clientAccount/{companyId}/{branchId}/allocations/{allocationId}/reverse`
- `POST /clientAccount/{companyId}/{branchId}/receipts/{receiptId}/reverse`
- `POST /clientAccount/{companyId}/{branchId}/open-items/{openItemId}/reverse`
- `POST /suppliers/{companyId}/{branchId}/{supplierId}/receipts/{receiptId}/allocations`
- `POST /suppliers/{companyId}/{branchId}/allocations/{allocationId}/reverse`
- `POST /suppliers/{companyId}/{branchId}/receipts/{receiptId}/reverse`
- `POST /suppliers/{companyId}/{branchId}/open-items/{openItemId}/reverse`

A receipt or open item cannot be reversed until its active allocations are reversed.

## Credit and debit notes

- `POST /clientAccount/{companyId}/credit-notes`
- `POST /clientAccount/{companyId}/{branchId}/{clientId}/credit-notes/{noteId}/apply`
- `POST /clientAccount/{companyId}/{branchId}/credit-notes/{noteId}/reverse`
- `POST /suppliers/{companyId}/debit-notes`
- `POST /suppliers/{companyId}/{branchId}/{supplierId}/debit-notes/{noteId}/apply`
- `POST /suppliers/{companyId}/{branchId}/debit-notes/{noteId}/reverse`

Note creation requires an `idempotencyKey`. Applications use the same allocation protocol as receipts. Applied notes must have their applications reversed before the note itself can be reversed.
