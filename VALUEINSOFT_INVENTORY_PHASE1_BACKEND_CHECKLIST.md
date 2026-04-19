# Valueinsoft Inventory Phase 1 Backend Checklist

This is the practical engineering checklist for Phase 1: backend foundation.

## Scope

Phase 1 includes:

- `quickFind` endpoint
- `browseCatalog` endpoint
- `analyzeInventory` endpoint
- preset CRUD contract
- server-side filtering and pagination correctness

Phase 1 does not include:

- final frontend redesign
- drawer UX
- final chip layout
- full preset UI

## Implementation Order

1. Create transport models
- request DTOs
- response DTOs
- pagination DTO
- summary DTO
- chip count DTO

2. Create controller
- `InventoryWorkspaceController`

3. Create service layer
- `InventoryWorkspaceService`
- `InventoryQuickFindService`
- `InventoryCatalogBrowseService`
- `InventoryAnalysisService`
- `InventoryPresetService`

4. Reuse and adapt existing inventory logic
- reuse current product search patterns from stage 7 product service
- reuse current inventory transaction SQL from stage 7 history
- preserve legacy product routes during migration

5. Repository work
- add server-side browse filters
- add summary counts
- add chip counts
- add movement analysis queries
- add preset repository

6. Security work
- verify and enforce capabilities

7. Testing
- endpoint tests
- service tests
- repository tests where possible

## Backend Acceptance Criteria

### quickFind

- exact barcode lookup works
- exact product ID lookup works
- exact serial lookup works
- fuzzy name fallback works
- empty query returns a safe idle response

### browseCatalog

- empty query returns paginated catalog
- all active filters are server-side
- sort is server-side
- pagination is server-side
- summary counts reflect current filters
- chip counts reflect current filters

### analyzeInventory

- date range is required
- preset date ranges work
- movement type filtering works
- ledger rows are paginated
- KPI summary is returned
- movement counts are returned

### presets

- personal preset CRUD works
- branch shared permission is enforced
- admin/global permission is enforced

## Known Workspace Constraint

This repository snapshot does not include the complete runnable backend project. The staged backend files here are reference material, not a safe compile target on their own.

Engineering should implement this checklist in the full Spring Boot backend project, using the staged inventory files here as source material.
