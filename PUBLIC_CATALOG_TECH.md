# 🛠 Public Catalog Backend Technical Documentation

## Overview
This feature enables a public-facing product catalog for multi-tenant users. It requires cross-schema data fetching (Public metadata -> Tenant-specific inventory).

## Components

### 1. Database Schema
- **Table**: `public.public_tenants`
  - Stores mapping of `tenant_code` to `tenant_id`.
  - Stores branding (colors, logo) and contact (WhatsApp, Socials).
- **Table**: `[tenant_schema].inventory_product`
  - Stores visibility toggles and online-specific metadata.

### 2. Controllers
- `AdminPublicCatalogController`: Manage branding and slugs. Requires `company.settings.edit`.
- `PublicCatalogController`: Read-only public access.

### 3. Service Layer
- `PublicCatalogService`: 
  - Resolves `tenant_code` to `tenant_id`.
  - Switches to tenant schema using `TenantSqlIdentifiers`.
  - Aggregates stock balances for availability status.

### 4. Security Configuration
- `/api/public/**` is whitelisted in `SecurityConfiguration.java` to allow unauthenticated access from the public frontend.

## Key SQL Queries
- Product retrieval uses a `LEFT JOIN` on `inventory_branch_stock_balance` to ensure the "In Stock" status is real-time.
- `COALESCE` is used to fallback to standard descriptions/images if online-specific ones are not provided.

## Maintenance
- Migration `V58`: Initial foundation.
- Migration `V59`: Contact and social links expansion.
