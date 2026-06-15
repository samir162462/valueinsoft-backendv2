# Bulk Product Import Database Error Fix

## Problem

Opening Bulk Product Import failed when loading import history:

```text
ERROR: relation "c_1101.inventory_import_batch" does not exist
```

The import history endpoint queries tenant tables such as:

- `c_1101.inventory_import_batch`
- `c_1101.inventory_import_row`
- `c_1101.inventory_import_error`
- `c_1101.inventory_import_audit_log`

Those tables are created by the database helper:

```sql
public.create_inventory_product_import_tables_for_tenant(schema_name text)
```

but the runtime code was assuming they already existed.

## Fix

- Updated `DbCompany` so new company schema provisioning calls:

```sql
SELECT public.create_inventory_product_import_tables_for_tenant('c_<companyId>')
```

- Updated `ProductImportRepository` so it ensures the tenant import tables exist before using import batch, row, or error tables.
- The repository caches successful setup per company ID, so it does not run the setup query on every SQL statement.

## Result

For existing tenants like `c_1101`, opening Bulk Product Import should now create the missing import tables automatically before reading history.

## Verification

Backend compile passed:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.5'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -DskipTests compile
```
