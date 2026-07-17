package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinanceObligationsReportModels;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbFinanceObligationsReport {

    private final NamedParameterJdbcTemplate jdbc;

    public DbFinanceObligationsReport(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FinanceObligationsReportModels.Page page(int companyId, int branchId, String side,
                                                     LocalDate asOfDate, String search,
                                                     int limit, int offset, boolean includePayroll) {
        boolean receivable = "RECEIVABLE".equals(side);
        String grouped = groupedSql(companyId, branchId, receivable, search, null, includePayroll);
        MapSqlParameterSource params = params(companyId, branchId, asOfDate, search)
                .addValue("limit", limit).addValue("offset", offset);
        List<FinanceObligationsReportModels.PartySummary> parties = jdbc.query(
                "WITH parties AS (" + grouped + ") SELECT * FROM parties "
                        + "ORDER BY overdue_amount DESC, remaining_amount DESC, party_name, party_id "
                        + "LIMIT :limit OFFSET :offset",
                params, partyMapper());
        Long count = jdbc.queryForObject("WITH parties AS (" + grouped + ") SELECT COUNT(*) FROM parties",
                params, Long.class);
        List<FinanceObligationsReportModels.CurrencyTotal> totals = totals(
                companyId, branchId, receivable, asOfDate, search, includePayroll);
        return new FinanceObligationsReportModels.Page(companyId, branchId, side, asOfDate,
                totals, parties, limit, offset, count == null ? 0 : count);
    }

    public FinanceObligationsReportModels.PartyDetails details(int companyId, int branchId, String side,
                                                                int partyId, String partyType, String currencyCode,
                                                                LocalDate asOfDate, boolean includePayroll) {
        boolean receivable = "RECEIVABLE".equals(side);
        MapSqlParameterSource params = params(companyId, branchId, asOfDate, null)
                .addValue("partyId", partyId).addValue("partyType", partyType).addValue("currency", currencyCode);
        List<FinanceObligationsReportModels.PartySummary> parties = jdbc.query(
                "WITH parties AS (" + groupedSql(companyId, branchId, receivable, null, partyId, includePayroll)
                        + ") SELECT * FROM parties WHERE party_type=:partyType",
                params, partyMapper());
        if (parties.isEmpty()) return null;

        String detailType = receivable
                ? ("SUPPLIER".equalsIgnoreCase(partyType) ? "SUPPLIER_RECEIVABLE" : "CLIENT_RECEIVABLE")
                : partyType;
        List<DocumentRow> rows = jdbc.query(documentSql(companyId, branchId, detailType), params, documentMapper());
        List<Long> ids = rows.stream().map(DocumentRow::openItemId).toList();
        Map<Long, List<FinanceObligationsReportModels.SourceLine>> sourceLines = sourceLines(
                companyId, branchId, detailType, rows);
        Map<Long, List<FinanceObligationsReportModels.SettlementDetail>> settlements = settlements(
                companyId, detailType, ids);
        List<FinanceObligationsReportModels.ObligationDocument> documents = rows.stream()
                .map(row -> row.toModel(asOfDate,
                        sourceLines.getOrDefault(row.openItemId(), List.of()),
                        settlements.getOrDefault(row.openItemId(), List.of())))
                .toList();
        return new FinanceObligationsReportModels.PartyDetails(companyId, branchId, side, asOfDate,
                parties.get(0), documents);
    }

    private List<FinanceObligationsReportModels.CurrencyTotal> totals(int companyId, int branchId,
                                                                      boolean receivable,
                                                                      LocalDate asOfDate, String search,
                                                                      boolean includePayroll) {
        String grouped = groupedSql(companyId, branchId, receivable, search, null, includePayroll);
        String sql = "WITH parties AS (" + grouped + ") SELECT currency_code,SUM(total_amount),"
                + "SUM(settled_amount),SUM(remaining_amount),SUM(overdue_amount),"
                + "COUNT(DISTINCT party_type||':'||party_id),SUM(open_document_count) FROM parties "
                + "GROUP BY currency_code ORDER BY currency_code";
        return jdbc.query(sql, params(companyId, branchId, asOfDate, search), (rs, rowNum) ->
                new FinanceObligationsReportModels.CurrencyTotal(rs.getString(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                        rs.getLong(6), rs.getLong(7)));
    }

    private String groupedSql(int companyId, int branchId, boolean receivable,
                              String search, Integer exactPartyId, boolean includePayroll) {
        String oi = receivable ? TenantSqlIdentifiers.arOpenItemTable(companyId)
                : TenantSqlIdentifiers.apOpenItemTable(companyId);
        String party = receivable ? TenantSqlIdentifiers.clientTable(companyId)
                : TenantSqlIdentifiers.supplierTable(companyId, branchId);
        String partyId = receivable ? "oi.client_id" : "oi.supplier_id";
        String joinId = receivable ? "p.c_id" : "p.\"supplierId\"";
        String name = receivable ? "p.\"clientName\"" : "p.\"SupplierName\"";
        String phone1 = receivable ? "p.\"clientPhone\"" : "p.\"supplierPhone1\"";
        String phone2 = receivable ? "NULL::varchar" : "p.\"supplierPhone2\"";
        String location = receivable ? "NULL::varchar" : "p.\"SupplierLocation\"";
        String whereParty = exactPartyId == null ? "" : " AND " + partyId + "=:partyId";
        String primary = "SELECT " + partyId + " party_id, '" + (receivable ? "CLIENT" : "SUPPLIER")
                + "' party_type, :branchId branch_id, " + name + " party_name, " + phone1
                + " primary_phone, " + phone2 + " secondary_phone, " + location + " location, "
                + "oi.currency_code, SUM(oi.total_amount) total_amount, SUM(oi.settled_amount) settled_amount, "
                + "SUM(oi.remaining_amount) remaining_amount, "
                + "COALESCE(SUM(oi.remaining_amount) FILTER (WHERE oi.due_date::date < :asOf),0) overdue_amount, "
                + "MIN(oi.due_date) oldest_due_date, COUNT(*) open_document_count, "
                + "COUNT(*) FILTER (WHERE oi.due_date::date < :asOf) overdue_document_count FROM " + oi
                + " oi JOIN " + party + " p ON " + joinId + "=" + partyId
                + " WHERE oi.company_id=:companyId AND oi.branch_id=:branchId "
                + (receivable ? "AND oi.party_type='CLIENT' " : "")
                + "AND oi.status IN ('OPEN','PARTIALLY_SETTLED') AND oi.document_date::date <= :asOf"
                + whereParty + (exactPartyId == null ? "" : " AND UPPER(oi.currency_code)=UPPER(:currency)")
                + searchClause(search, name, phone1)
                + " GROUP BY " + partyId + "," + name + "," + phone1 + "," + phone2 + "," + location
                + ",oi.currency_code";
        if (receivable) {
            String suppliers = TenantSqlIdentifiers.supplierTable(companyId, branchId);
            String supplierWhere = exactPartyId == null ? "" : " AND oi.supplier_id=:partyId AND UPPER(oi.currency_code)=UPPER(:currency)";
            String supplierReceivables = "SELECT oi.supplier_id party_id,'SUPPLIER' party_type,:branchId branch_id,"
                    + "s.\"SupplierName\" party_name,s.\"supplierPhone1\" primary_phone,s.\"supplierPhone2\" secondary_phone,"
                    + "s.\"SupplierLocation\" location,oi.currency_code,SUM(oi.total_amount) total_amount,"
                    + "SUM(oi.settled_amount) settled_amount,SUM(oi.remaining_amount) remaining_amount,"
                    + "COALESCE(SUM(oi.remaining_amount) FILTER (WHERE oi.due_date::date<:asOf),0) overdue_amount,"
                    + "MIN(oi.due_date) oldest_due_date,COUNT(*) open_document_count,"
                    + "COUNT(*) FILTER (WHERE oi.due_date::date<:asOf) overdue_document_count FROM " + oi
                    + " oi JOIN " + suppliers + " s ON s.\"supplierId\"=oi.supplier_id "
                    + "WHERE oi.company_id=:companyId AND oi.branch_id=:branchId AND oi.party_type='SUPPLIER' "
                    + "AND oi.status IN ('OPEN','PARTIALLY_SETTLED') AND oi.document_date::date<=:asOf"
                    + supplierWhere + searchClause(search, "s.\"SupplierName\"", "s.\"supplierPhone1\"")
                    + " GROUP BY oi.supplier_id,s.\"SupplierName\",s.\"supplierPhone1\",s.\"supplierPhone2\","
                    + "s.\"SupplierLocation\",oi.currency_code";
            return primary + " UNION ALL " + supplierReceivables;
        }

        String tradeIns = TenantSqlIdentifiers.clientTradeInReceiptTable(companyId);
        String clients = TenantSqlIdentifiers.clientTable(companyId);
        String tradeInSearch = searchClause(search, "c.\"clientName\"", "c.\"clientPhone\"")
                .replace("oi.document_ref", "tr.receipt_reference");
        String clientPayables = "SELECT tr.client_id party_id,'CLIENT' party_type,:branchId branch_id,"
                + "c.\"clientName\" party_name,c.\"clientPhone\" primary_phone,NULL::varchar secondary_phone,"
                + "NULL::varchar location,co.currency currency_code,SUM(tr.total_amount) total_amount,"
                + "SUM(tr.paid_amount) settled_amount,SUM(tr.remaining_amount) remaining_amount,"
                + "SUM(tr.remaining_amount) FILTER (WHERE tr.created_at::date<:asOf) overdue_amount,"
                + "MIN(tr.created_at) oldest_due_date,COUNT(*) open_document_count,"
                + "COUNT(*) FILTER (WHERE tr.created_at::date<:asOf) overdue_document_count FROM " + tradeIns
                + " tr JOIN " + clients + " c ON c.c_id=tr.client_id JOIN public.\"Company\" co ON co.id=tr.company_id "
                + "WHERE tr.company_id=:companyId AND tr.branch_id=:branchId AND tr.status='POSTED' "
                + "AND tr.payment_status IN ('UNPAID','PARTIALLY_PAID') AND tr.created_at::date<=:asOf"
                + (exactPartyId == null ? "" : " AND tr.client_id=:partyId AND UPPER(co.currency)=UPPER(:currency)")
                + tradeInSearch + " GROUP BY tr.client_id,c.\"clientName\",c.\"clientPhone\",co.currency";

        String runs = TenantSqlIdentifiers.payrollRunTable(companyId);
        String runLines = TenantSqlIdentifiers.payrollRunLineTable(companyId);
        String employees = TenantSqlIdentifiers.hrEmployeeTable(companyId);
        String employeeSearch = searchClause(search, "(e.first_name||' '||COALESCE(e.last_name,''))", "e.employee_code")
                .replace("oi.document_ref", "pr.run_label");
        String employeePayables = "SELECT prl.employee_id party_id,'EMPLOYEE' party_type,:branchId branch_id,"
                + "TRIM(e.first_name||' '||COALESCE(e.last_name,'')) party_name,e.employee_code primary_phone,"
                + "NULL::varchar secondary_phone,NULL::varchar location,pr.currency_code,SUM(prl.net_salary) total_amount,"
                + "SUM(prl.paid_amount) settled_amount,SUM(prl.remaining_amount) remaining_amount,"
                + "SUM(prl.remaining_amount) FILTER (WHERE pr.period_end<:asOf) overdue_amount,"
                + "MIN(pr.period_end)::timestamp oldest_due_date,COUNT(*) open_document_count,"
                + "COUNT(*) FILTER (WHERE pr.period_end<:asOf) overdue_document_count FROM " + runLines
                + " prl JOIN " + runs + " pr ON pr.id=prl.payroll_run_id JOIN " + employees
                + " e ON e.id=prl.employee_id WHERE prl.company_id=:companyId AND e.branch_id=:branchId "
                + "AND pr.status IN ('POSTED','PARTIALLY_PAID') AND prl.payment_status IN ('UNPAID','PARTIALLY_PAID') "
                + "AND pr.period_end<=:asOf"
                + (exactPartyId == null ? "" : " AND prl.employee_id=:partyId AND UPPER(pr.currency_code)=UPPER(:currency)")
                + employeeSearch + " GROUP BY prl.employee_id,e.first_name,e.last_name,e.employee_code,pr.currency_code";
        return primary + " UNION ALL " + clientPayables
                + (includePayroll ? " UNION ALL " + employeePayables : "");
    }

    private String documentSql(int companyId, int branchId, String partyType) {
        if ("SUPPLIER_RECEIVABLE".equals(partyType)) {
            String oi = TenantSqlIdentifiers.arOpenItemTable(companyId);
            String orders = TenantSqlIdentifiers.orderTable(companyId, branchId);
            String details = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
            return "SELECT oi.*,COALESCE(o.receipt_number,oi.document_ref) source_reference,"
                    + "o.\"orderType\" source_method,o.\"salesUser\" source_actor,o.\"orderTime\" source_date,"
                    + "o.\"orderTotal\"::numeric source_gross_amount,COALESCE(d.line_count,0) source_line_count FROM "
                    + oi + " oi LEFT JOIN " + orders + " o ON o.\"orderId\"=oi.source_id LEFT JOIN "
                    + "(SELECT \"orderId\",COUNT(*) line_count FROM " + details + " GROUP BY \"orderId\") d ON d.\"orderId\"=o.\"orderId\" "
                    + "WHERE oi.company_id=:companyId AND oi.branch_id=:branchId AND oi.party_type='SUPPLIER' "
                    + "AND oi.supplier_id=:partyId AND UPPER(oi.currency_code)=UPPER(:currency) "
                    + "AND oi.status IN ('OPEN','PARTIALLY_SETTLED') AND oi.document_date::date<=:asOf "
                    + "ORDER BY oi.due_date NULLS LAST,oi.document_date,oi.open_item_id";
        }
        if ("CLIENT_RECEIVABLE".equals(partyType)) partyType = "CLIENT";
        if ("CLIENT".equals(partyType)) {
            String oi = TenantSqlIdentifiers.arOpenItemTable(companyId);
            String orders = TenantSqlIdentifiers.orderTable(companyId, branchId);
            String details = TenantSqlIdentifiers.orderDetailTable(companyId, branchId);
            return "SELECT oi.*, COALESCE(o.receipt_number,oi.document_ref) source_reference, "
                    + "o.\"orderType\" source_method,o.\"salesUser\" source_actor,o.\"orderTime\" source_date,"
                    + "o.\"orderTotal\"::numeric source_gross_amount,COALESCE(d.line_count,0) source_line_count FROM "
                    + oi + " oi LEFT JOIN " + orders + " o ON oi.source_type='POS_ORDER' "
                    + "AND o.\"orderId\"=oi.source_id LEFT JOIN (SELECT \"orderId\",COUNT(*) line_count FROM "
                    + details + " GROUP BY \"orderId\") d ON d.\"orderId\"=o.\"orderId\" "
                    + "WHERE oi.company_id=:companyId AND oi.branch_id=:branchId AND oi.client_id=:partyId "
                    + "AND UPPER(oi.currency_code)=UPPER(:currency) AND oi.status IN ('OPEN','PARTIALLY_SETTLED') "
                    + "AND oi.document_date::date<=:asOf "
                    + "ORDER BY oi.due_date NULLS LAST,oi.document_date,oi.open_item_id";
        }
        if ("SUPPLIER".equals(partyType)) {
        String oi = TenantSqlIdentifiers.apOpenItemTable(companyId);
        String ledger = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);
        return "SELECT oi.*,COALESCE(l.reference_id,oi.document_ref) source_reference,l.pay_type source_method,"
                + "l.actor_name source_actor,l.created_at source_date,l.trans_total::numeric source_gross_amount,"
                + "CASE WHEN l.stock_ledger_id IS NULL THEN 0 ELSE 1 END source_line_count FROM " + oi
                + " oi LEFT JOIN " + ledger + " l ON oi.source_type='PURCHASE' AND l.stock_ledger_id=oi.source_id "
                + "WHERE oi.company_id=:companyId AND oi.branch_id=:branchId AND oi.supplier_id=:partyId "
                + "AND UPPER(oi.currency_code)=UPPER(:currency) AND oi.status IN ('OPEN','PARTIALLY_SETTLED') "
                + "AND oi.document_date::date<=:asOf "
                + "ORDER BY oi.due_date NULLS LAST,oi.document_date,oi.open_item_id";
        }
        if ("EMPLOYEE".equals(partyType)) return payrollDocumentSql(companyId);
        return clientTradeInDocumentSql(companyId);
    }

    private String clientTradeInDocumentSql(int companyId) {
        return "SELECT tr.tradein_receipt_id open_item_id,'CLIENT_TRADE_IN' source_type,"
                + "tr.stock_ledger_id source_id,tr.receipt_reference document_ref,tr.created_at document_date,"
                + "tr.created_at due_date,co.currency currency_code,tr.total_amount,tr.paid_amount settled_amount,"
                + "tr.remaining_amount,tr.payment_status status,tr.condition_notes notes,tr.receipt_reference source_reference,"
                + "tr.payment_method source_method,tr.created_by source_actor,tr.created_at source_date,"
                + "tr.total_amount source_gross_amount,tr.quantity source_line_count FROM "
                + TenantSqlIdentifiers.clientTradeInReceiptTable(companyId)
                + " tr JOIN public.\"Company\" co ON co.id=tr.company_id WHERE tr.company_id=:companyId "
                + "AND tr.branch_id=:branchId AND tr.client_id=:partyId AND UPPER(co.currency)=UPPER(:currency) "
                + "AND tr.status='POSTED' AND tr.payment_status IN ('UNPAID','PARTIALLY_PAID') "
                + "AND tr.created_at::date<=:asOf ORDER BY tr.created_at,tr.tradein_receipt_id";
    }

    private String payrollDocumentSql(int companyId) {
        return "SELECT prl.id open_item_id,'PAYROLL_RUN' source_type,pr.id source_id,"
                + "COALESCE(pr.run_label,'PAYROLL-'||pr.id) document_ref,pr.created_at document_date,"
                + "pr.period_end::timestamp due_date,pr.currency_code,prl.net_salary total_amount,"
                + "prl.paid_amount settled_amount,prl.remaining_amount,prl.payment_status status,prl.notes,"
                + "COALESCE(pr.run_label,'PAYROLL-'||pr.id) source_reference,pr.frequency source_method,"
                + "COALESCE(pr.approved_by,pr.created_by) source_actor,COALESCE(pr.posted_at,pr.created_at) source_date,"
                + "prl.net_salary source_gross_amount,1 source_line_count FROM "
                + TenantSqlIdentifiers.payrollRunLineTable(companyId) + " prl JOIN "
                + TenantSqlIdentifiers.payrollRunTable(companyId) + " pr ON pr.id=prl.payroll_run_id JOIN "
                + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " e ON e.id=prl.employee_id "
                + "WHERE prl.company_id=:companyId AND e.branch_id=:branchId AND prl.employee_id=:partyId "
                + "AND UPPER(pr.currency_code)=UPPER(:currency) AND pr.status IN ('POSTED','PARTIALLY_PAID') "
                + "AND prl.payment_status IN ('UNPAID','PARTIALLY_PAID') AND pr.period_end<=:asOf "
                + "ORDER BY pr.period_end,prl.id";
    }

    private String clientTradeInSettlementSql(int companyId) {
        return "SELECT a.tradein_receipt_id open_item_id,a.allocation_id,'CLIENT_PAYOUT' source_type,"
                + "p.payment_id source_id,'PAYOUT-'||p.payment_id source_reference,p.payment_method,"
                + "p.created_by recorded_by,p.created_at recorded_at,a.amount,p.status,"
                + "NULL::bigint reversal_of_allocation_id FROM "
                + TenantSqlIdentifiers.clientTradeInPaymentAllocationTable(companyId) + " a JOIN "
                + TenantSqlIdentifiers.clientTradeInPaymentTable(companyId) + " p ON p.payment_id=a.payment_id "
                + "WHERE a.company_id=:companyId AND a.tradein_receipt_id IN (:ids) "
                + "ORDER BY p.created_at,a.allocation_id";
    }

    private String payrollSettlementSql(int companyId) {
        return "SELECT ppl.payroll_run_line_id open_item_id,ppl.id allocation_id,'PAYROLL_PAYMENT' source_type,"
                + "p.id source_id,COALESCE(p.reference_number,'PAYROLL-PAYMENT-'||p.id) source_reference,"
                + "COALESCE(ppl.payment_method,p.payment_method) payment_method,p.created_by recorded_by,"
                + "p.payment_date::timestamp recorded_at,ppl.paid_amount amount,p.status,"
                + "NULL::bigint reversal_of_allocation_id FROM "
                + TenantSqlIdentifiers.payrollPaymentLineTable(companyId) + " ppl JOIN "
                + TenantSqlIdentifiers.payrollPaymentTable(companyId) + " p ON p.id=ppl.payroll_payment_id "
                + "WHERE ppl.company_id=:companyId AND ppl.payroll_run_line_id IN (:ids) "
                + "ORDER BY p.payment_date,ppl.id";
    }

    private Map<Long, List<FinanceObligationsReportModels.SourceLine>> sourceLines(
            int companyId, int branchId, String partyType, List<DocumentRow> documents) {
        if (documents.isEmpty()) return Map.of();
        Map<Long, Long> sourceToDocument = new LinkedHashMap<>();
        for (DocumentRow document : documents) {
            if (document.sourceId() != null) sourceToDocument.put(document.sourceId(), document.openItemId());
        }
        Map<Long, List<FinanceObligationsReportModels.SourceLine>> result = new LinkedHashMap<>();
        if ("EMPLOYEE".equals(partyType)) {
            for (DocumentRow document : documents) {
                result.put(document.openItemId(), List.of(new FinanceObligationsReportModels.SourceLine(
                        document.openItemId(), null, "Net salary", BigDecimal.ONE,
                        document.totalAmount(), document.totalAmount(), document.notes())));
            }
            return result;
        }
        if (sourceToDocument.isEmpty()) return result;
        String sql;
        if ("CLIENT_RECEIVABLE".equals(partyType) || "SUPPLIER_RECEIVABLE".equals(partyType)) {
            sql = "SELECT d.\"orderId\" source_id,d.\"orderDetailsId\" line_id,"
                    + "COALESCE(d.\"productId\",d.\"itemId\")::bigint item_id,d.\"itemName\" item_name,"
                    + "d.\"quantity\"::numeric quantity,d.\"price\"::numeric unit_amount,d.\"total\"::numeric total_amount,"
                    + "CASE WHEN COALESCE(d.\"bouncedBack\",0)>0 THEN 'RETURNED' END notes FROM "
                    + TenantSqlIdentifiers.orderDetailTable(companyId, branchId)
                    + " d WHERE d.\"orderId\" IN (:sourceIds) ORDER BY d.\"orderId\",d.\"orderDetailsId\"";
        } else if ("SUPPLIER".equals(partyType)) {
            sql = "SELECT l.stock_ledger_id source_id,l.stock_ledger_id line_id,l.product_id item_id,"
                    + "p.product_name item_name,ABS(l.quantity_delta)::numeric quantity,"
                    + "CASE WHEN ABS(l.quantity_delta)=0 THEN l.trans_total::numeric ELSE l.trans_total::numeric/ABS(l.quantity_delta) END unit_amount,"
                    + "l.trans_total::numeric total_amount,l.note notes FROM "
                    + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) + " l LEFT JOIN "
                    + TenantSqlIdentifiers.inventoryProductTable(companyId) + " p ON p.product_id=l.product_id "
                    + "WHERE l.stock_ledger_id IN (:sourceIds) ORDER BY l.stock_ledger_id";
        } else {
            sql = "SELECT tr.stock_ledger_id source_id,tr.tradein_receipt_id line_id,tr.product_id item_id,"
                    + "p.product_name item_name,tr.quantity::numeric quantity,tr.unit_cost unit_amount,"
                    + "tr.total_amount,tr.condition_notes notes FROM "
                    + TenantSqlIdentifiers.clientTradeInReceiptTable(companyId) + " tr LEFT JOIN "
                    + TenantSqlIdentifiers.inventoryProductTable(companyId) + " p ON p.product_id=tr.product_id "
                    + "WHERE tr.stock_ledger_id IN (:sourceIds) ORDER BY tr.tradein_receipt_id";
        }
        jdbc.query(sql, new MapSqlParameterSource("sourceIds", sourceToDocument.keySet()), rs -> {
            Long openItemId = sourceToDocument.get(rs.getLong("source_id"));
            if (openItemId == null) return;
            result.computeIfAbsent(openItemId, ignored -> new ArrayList<>()).add(
                    new FinanceObligationsReportModels.SourceLine(rs.getLong("line_id"),
                            (Long) rs.getObject("item_id", Long.class), rs.getString("item_name"),
                            rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_amount"),
                            rs.getBigDecimal("total_amount"), rs.getString("notes")));
        });
        return result;
    }

    private Map<Long, List<FinanceObligationsReportModels.SettlementDetail>> settlements(
            int companyId, String partyType, List<Long> openItemIds) {
        if (openItemIds.isEmpty()) return Map.of();
        String sql;
        if ("SUPPLIER_RECEIVABLE".equals(partyType)) {
            sql = "SELECT a.open_item_id,a.allocation_id,'SUPPLIER_RECEIPT' source_type,r.receipt_id source_id,"
                    + "'SUPPLIER-RECEIPT-'||r.receipt_id source_reference,r.payment_method,r.created_by recorded_by,"
                    + "r.created_at recorded_at,a.amount,a.status,NULL::bigint reversal_of_allocation_id FROM "
                    + TenantSqlIdentifiers.arSupplierReceiptAllocationTable(companyId) + " a JOIN "
                    + TenantSqlIdentifiers.arSupplierReceiptTable(companyId) + " r ON r.receipt_id=a.receipt_id "
                    + "WHERE a.company_id=:companyId AND a.open_item_id IN (:ids) ORDER BY a.created_at,a.allocation_id";
        } else if ("CLIENT_RECEIVABLE".equals(partyType)) {
            sql = "SELECT a.open_item_id,a.allocation_id,CASE WHEN a.receipt_id IS NOT NULL THEN 'RECEIPT' ELSE 'CREDIT_NOTE' END source_type,"
                    + "COALESCE(a.receipt_id::bigint,a.credit_note_id) source_id,"
                    + "CASE WHEN a.receipt_id IS NOT NULL THEN 'RECEIPT-'||a.receipt_id ELSE 'CREDIT-NOTE-'||a.credit_note_id END source_reference,"
                    + "COALESCE(r.payment_method,r.type,n.reference_type) payment_method,COALESCE(r.\"userName\",n.created_by) recorded_by,"
                    + "COALESCE(r.\"time\",n.created_at) recorded_at,a.amount,a.status,a.reversal_of_allocation_id FROM "
                    + TenantSqlIdentifiers.arReceiptAllocationTable(companyId) + " a LEFT JOIN "
                    + TenantSqlIdentifiers.clientReceiptsTable(companyId) + " r ON r.\"crId\"=a.receipt_id LEFT JOIN "
                    + TenantSqlIdentifiers.arCreditNoteTable(companyId) + " n ON n.credit_note_id=a.credit_note_id "
                    + "WHERE a.company_id=:companyId AND a.open_item_id IN (:ids) ORDER BY a.created_at,a.allocation_id";
        } else if ("SUPPLIER".equals(partyType)) {
            sql = "SELECT a.open_item_id,a.allocation_id,CASE WHEN a.receipt_id IS NOT NULL THEN 'RECEIPT' ELSE 'DEBIT_NOTE' END source_type,"
                    + "COALESCE(a.receipt_id::bigint,a.debit_note_id) source_id,"
                    + "CASE WHEN a.receipt_id IS NOT NULL THEN 'RECEIPT-'||a.receipt_id ELSE 'DEBIT-NOTE-'||a.debit_note_id END source_reference,"
                    + "COALESCE(r.type,n.reference_type) payment_method,COALESCE(r.\"userRecived\",n.created_by) recorded_by,"
                    + "COALESCE(r.\"receiptTime\",n.created_at) recorded_at,a.amount,a.status,a.reversal_of_allocation_id FROM "
                    + TenantSqlIdentifiers.apPaymentAllocationTable(companyId) + " a LEFT JOIN "
                    + TenantSqlIdentifiers.supplierReceiptsTable(companyId) + " r ON r.\"srId\"=a.receipt_id LEFT JOIN "
                    + TenantSqlIdentifiers.apDebitNoteTable(companyId) + " n ON n.debit_note_id=a.debit_note_id "
                    + "WHERE a.company_id=:companyId AND a.open_item_id IN (:ids) ORDER BY a.created_at,a.allocation_id";
        } else if ("EMPLOYEE".equals(partyType)) {
            sql = payrollSettlementSql(companyId);
        } else {
            sql = clientTradeInSettlementSql(companyId);
        }
        Map<Long, List<FinanceObligationsReportModels.SettlementDetail>> result = new LinkedHashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("companyId", companyId).addValue("ids", openItemIds), rs -> {
            result.computeIfAbsent(rs.getLong("open_item_id"), ignored -> new ArrayList<>()).add(
                    new FinanceObligationsReportModels.SettlementDetail(rs.getLong("allocation_id"),
                            rs.getString("source_type"), rs.getLong("source_id"), rs.getString("source_reference"),
                            rs.getString("payment_method"), rs.getString("recorded_by"), timestamp(rs.getTimestamp("recorded_at")),
                            rs.getBigDecimal("amount"), rs.getString("status"),
                            (Long) rs.getObject("reversal_of_allocation_id", Long.class)));
        });
        return result;
    }

    private static RowMapper<FinanceObligationsReportModels.PartySummary> partyMapper() {
        return (rs, rowNum) -> new FinanceObligationsReportModels.PartySummary(rs.getInt("party_id"),
                rs.getString("party_type"), rs.getInt("branch_id"), rs.getString("party_name"),
                rs.getString("primary_phone"), rs.getString("secondary_phone"), rs.getString("location"),
                rs.getString("currency_code"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("settled_amount"),
                rs.getBigDecimal("remaining_amount"), rs.getBigDecimal("overdue_amount"),
                timestamp(rs.getTimestamp("oldest_due_date")), rs.getInt("open_document_count"),
                rs.getInt("overdue_document_count"));
    }

    private static RowMapper<DocumentRow> documentMapper() {
        return (rs, rowNum) -> new DocumentRow(rs.getLong("open_item_id"), rs.getString("source_type"),
                (Long) rs.getObject("source_id", Long.class), rs.getString("document_ref"),
                timestamp(rs.getTimestamp("document_date")), timestamp(rs.getTimestamp("due_date")),
                rs.getString("currency_code"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("settled_amount"),
                rs.getBigDecimal("remaining_amount"), rs.getString("status"), rs.getString("notes"),
                rs.getString("source_reference"), rs.getString("source_method"), rs.getString("source_actor"),
                timestamp(rs.getTimestamp("source_date")), rs.getBigDecimal("source_gross_amount"),
                rs.getInt("source_line_count"));
    }

    private static String searchClause(String search, String name, String phone) {
        return search == null || search.isBlank() ? "" : " AND (LOWER(COALESCE(" + name
                + ",'')) LIKE :search OR LOWER(COALESCE(" + phone + ",'')) LIKE :search "
                + "OR LOWER(COALESCE(oi.document_ref,'')) LIKE :search)";
    }

    private static MapSqlParameterSource params(int companyId, int branchId, LocalDate asOf, String search) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("branchId", branchId).addValue("asOf", asOf);
        if (search != null && !search.isBlank()) params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        return params;
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record DocumentRow(long openItemId, String sourceType, Long sourceId, String documentReference,
                               LocalDateTime documentDate, LocalDateTime dueDate, String currencyCode,
                               BigDecimal totalAmount, BigDecimal settledAmount, BigDecimal remainingAmount,
                               String status, String notes, String sourceReference, String paymentMethod,
                               String sourceActor, LocalDateTime sourceDate, BigDecimal sourceGrossAmount,
                               int sourceLineCount) {
        FinanceObligationsReportModels.ObligationDocument toModel(
                LocalDate asOf, List<FinanceObligationsReportModels.SourceLine> sourceLines,
                List<FinanceObligationsReportModels.SettlementDetail> settlements) {
            LocalDate due = dueDate == null ? null : dueDate.toLocalDate();
            long overdueDays = due != null && due.isBefore(asOf) ? ChronoUnit.DAYS.between(due, asOf) : 0;
            String dueState = due == null || due.isAfter(asOf) ? "CURRENT" : due.isEqual(asOf) ? "DUE_TODAY" : "OVERDUE";
            return new FinanceObligationsReportModels.ObligationDocument(openItemId, sourceType, sourceId,
                    documentReference, documentDate, dueDate, dueState, overdueDays, currencyCode,
                    totalAmount, settledAmount, remainingAmount, status, notes, sourceReference,
                    paymentMethod, sourceActor, sourceDate, sourceGrossAmount, sourceLineCount,
                    sourceLines, settlements);
        }
    }
}
