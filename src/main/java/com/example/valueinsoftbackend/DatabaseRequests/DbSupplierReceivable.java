package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DbSupplierReceivable {
    private final NamedParameterJdbcTemplate jdbc;

    public DbSupplierReceivable(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean supplierExists(int companyId, int branchId, int supplierId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "
                        + TenantSqlIdentifiers.supplierTable(companyId, branchId) + " WHERE \"supplierId\"=:supplierId)",
                new MapSqlParameterSource("supplierId", supplierId), Boolean.class);
        return Boolean.TRUE.equals(found);
    }

    public long createOpenItem(int companyId, int branchId, int supplierId, long orderId,
                               LocalDateTime orderTime, String currency, BigDecimal total,
                               BigDecimal paidNow, String idempotencyKey, String actor) {
        BigDecimal remaining = total.subtract(paidNow);
        String status = paidNow.signum() > 0 ? "PARTIALLY_SETTLED" : "OPEN";
        MapSqlParameterSource p = new MapSqlParameterSource("companyId", companyId)
                .addValue("branchId", branchId).addValue("supplierId", supplierId)
                .addValue("orderId", orderId).addValue("documentRef", "POS-SUP-" + orderId)
                .addValue("orderTime", Timestamp.valueOf(orderTime)).addValue("currency", currency)
                .addValue("total", total).addValue("paid", paidNow).addValue("remaining", remaining)
                .addValue("status", status).addValue("key", idempotencyKey).addValue("actor", actor);
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.arOpenItemTable(companyId)
                        + " (company_id,branch_id,client_id,party_type,supplier_id,source_type,source_id,document_ref,"
                        + "document_date,due_date,currency_code,total_amount,settled_amount,remaining_amount,status,"
                        + "idempotency_key,created_by) VALUES (:companyId,:branchId,NULL,'SUPPLIER',:supplierId,"
                        + "'POS_SUPPLIER_ORDER',:orderId,:documentRef,:orderTime,:orderTime,:currency,:total,:paid,"
                        + ":remaining,:status,:key,:actor) ON CONFLICT (company_id,branch_id,source_type,source_id) "
                        + "WHERE source_id IS NOT NULL DO UPDATE SET source_id=EXCLUDED.source_id RETURNING open_item_id",
                p, Long.class);
    }

    public long createReceiptAndAllocation(int companyId, int branchId, int supplierId, long openItemId,
                                           long orderId, BigDecimal amount, String currency,
                                           String idempotencyKey, String actor) {
        MapSqlParameterSource p = new MapSqlParameterSource("companyId", companyId)
                .addValue("branchId", branchId).addValue("supplierId", supplierId)
                .addValue("openItemId", openItemId).addValue("orderId", String.valueOf(orderId))
                .addValue("amount", amount).addValue("currency", currency)
                .addValue("key", idempotencyKey).addValue("actor", actor);
        Long receiptId = jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.arSupplierReceiptTable(companyId)
                        + " (company_id,branch_id,supplier_id,amount,currency_code,payment_method,reference_type,"
                        + "reference_id,idempotency_key,notes,created_by) VALUES (:companyId,:branchId,:supplierId,"
                        + ":amount,:currency,'CASH','POS_ORDER',:orderId,:key,'Cash received during POS checkout',:actor) "
                        + "ON CONFLICT (company_id,idempotency_key) DO UPDATE SET idempotency_key=EXCLUDED.idempotency_key "
                        + "RETURNING receipt_id", p, Long.class);
        jdbc.update("INSERT INTO " + TenantSqlIdentifiers.arSupplierReceiptAllocationTable(companyId)
                        + " (company_id,branch_id,supplier_id,receipt_id,open_item_id,amount,currency_code,idempotency_key,created_by) "
                        + "VALUES (:companyId,:branchId,:supplierId,:receiptId,:openItemId,:amount,:currency,:allocationKey,:actor) "
                        + "ON CONFLICT (company_id,idempotency_key) DO NOTHING",
                p.addValue("receiptId", receiptId).addValue("allocationKey", idempotencyKey + ":allocation"));
        return receiptId == null ? 0 : receiptId;
    }

    public List<OpenBalance> findOpenForUpdate(int companyId, int branchId, int supplierId, String currency) {
        return jdbc.query("SELECT open_item_id,settled_amount,remaining_amount FROM "
                        + TenantSqlIdentifiers.arOpenItemTable(companyId)
                        + " WHERE company_id=:companyId AND branch_id=:branchId AND party_type='SUPPLIER' "
                        + "AND supplier_id=:supplierId AND UPPER(currency_code)=UPPER(:currency) "
                        + "AND status IN ('OPEN','PARTIALLY_SETTLED') ORDER BY due_date NULLS LAST,document_date,open_item_id FOR UPDATE",
                new MapSqlParameterSource("companyId", companyId).addValue("branchId", branchId)
                        .addValue("supplierId", supplierId).addValue("currency", currency),
                (rs, rowNum) -> new OpenBalance(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)));
    }

    public long createReceipt(int companyId, int branchId, int supplierId, BigDecimal amount, String currency,
                              String paymentMethod, String idempotencyKey, String actor) {
        Long id = jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.arSupplierReceiptTable(companyId)
                        + " (company_id,branch_id,supplier_id,amount,currency_code,payment_method,reference_type,"
                        + "idempotency_key,notes,created_by) VALUES (:companyId,:branchId,:supplierId,:amount,:currency,"
                        + ":method,'SUPPLIER_COLLECTION',:key,'Later payment received from supplier-customer',:actor) "
                        + "ON CONFLICT (company_id,idempotency_key) DO UPDATE SET idempotency_key=EXCLUDED.idempotency_key RETURNING receipt_id",
                new MapSqlParameterSource("companyId", companyId).addValue("branchId", branchId)
                        .addValue("supplierId", supplierId).addValue("amount", amount).addValue("currency", currency)
                        .addValue("method", paymentMethod).addValue("key", idempotencyKey).addValue("actor", actor), Long.class);
        return id == null ? 0 : id;
    }

    public Long findReceiptByIdempotency(int companyId, String key) {
        List<Long> ids = jdbc.query("SELECT receipt_id FROM " + TenantSqlIdentifiers.arSupplierReceiptTable(companyId)
                        + " WHERE company_id=:companyId AND idempotency_key=:key",
                new MapSqlParameterSource("companyId", companyId).addValue("key", key),
                (rs, rowNum) -> rs.getLong(1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void allocateAndUpdate(int companyId, int branchId, int supplierId, long receiptId,
                                  OpenBalance item, BigDecimal amount, String currency, String key, String actor) {
        int inserted = jdbc.update("INSERT INTO " + TenantSqlIdentifiers.arSupplierReceiptAllocationTable(companyId)
                        + " (company_id,branch_id,supplier_id,receipt_id,open_item_id,amount,currency_code,idempotency_key,created_by) "
                        + "VALUES (:companyId,:branchId,:supplierId,:receiptId,:itemId,:amount,:currency,:key,:actor) "
                        + "ON CONFLICT (company_id,idempotency_key) DO NOTHING",
                new MapSqlParameterSource("companyId", companyId).addValue("branchId", branchId)
                        .addValue("supplierId", supplierId).addValue("receiptId", receiptId)
                        .addValue("itemId", item.openItemId()).addValue("amount", amount).addValue("currency", currency)
                        .addValue("key", key).addValue("actor", actor));
        if (inserted == 0) return;
        BigDecimal settled = item.settledAmount().add(amount);
        BigDecimal remaining = item.remainingAmount().subtract(amount);
        jdbc.update("UPDATE " + TenantSqlIdentifiers.arOpenItemTable(companyId)
                        + " SET settled_amount=:settled,remaining_amount=:remaining,status=:status,updated_by=:actor,"
                        + "updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE open_item_id=:itemId",
                new MapSqlParameterSource("settled", settled).addValue("remaining", remaining)
                        .addValue("status", remaining.signum() == 0 ? "SETTLED" : "PARTIALLY_SETTLED")
                        .addValue("actor", actor).addValue("itemId", item.openItemId()));
    }

    public record OpenBalance(long openItemId, BigDecimal settledAmount, BigDecimal remainingAmount) {}
}
