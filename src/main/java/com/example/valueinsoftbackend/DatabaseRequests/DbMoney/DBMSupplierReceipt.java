package com.example.valueinsoftbackend.DatabaseRequests.DbMoney;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class DBMSupplierReceipt {

    private static final RowMapper<SupplierReceipt> supplierReceiptRowMapper = (rs, rowNum) -> new SupplierReceipt(
            rs.getInt("srId"),
            rs.getInt("transId"),
            rs.getBigDecimal("amountPaid"),
            rs.getBigDecimal("remainingAmount"),
            rs.getTimestamp("receiptTime"),
            rs.getString("userRecived"),
            rs.getInt("supplierId"),
            rs.getString("type"),
            rs.getInt("branchId")
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DBMSupplierReceipt(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(supplierId, "supplierId");
        String sql = "SELECT \"srId\", \"transId\", \"amountPaid\"::money::numeric AS \"amountPaid\", " +
                "\"remainingAmount\"::money::numeric AS \"remainingAmount\", \"receiptTime\", \"userRecived\", " +
                "\"supplierId\", type, \"branchId\" FROM " + TenantSqlIdentifiers.companySchema(companyId) +
                ".\"supplierReciepts\" WHERE \"supplierId\" = ? ORDER BY \"receiptTime\" DESC";
        return jdbcTemplate.query(sql, supplierReceiptRowMapper, supplierId);
    }

    public String addSupplierReceipt(int companyId, SupplierReceipt supplierReceipt) {
        TenantSqlIdentifiers.requirePositive(supplierReceipt.getBranchId(), "branchId");
        String receiptsTable = TenantSqlIdentifiers.companySchema(companyId) + ".\"supplierReciepts\"";
        String inventoryTable = TenantSqlIdentifiers.inventoryTransactionsTable(companyId, supplierReceipt.getBranchId());
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, supplierReceipt.getBranchId());

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement insertReceipt = connection.prepareStatement(
                    "INSERT INTO " + receiptsTable +
                            " (\"transId\", \"amountPaid\", \"remainingAmount\", \"receiptTime\", \"userRecived\", \"supplierId\", type, \"branchId\") " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement updateInventory = connection.prepareStatement(
                         "UPDATE " + inventoryTable + " SET \"RemainingAmount\" = ? WHERE \"transId\" = ?");
                 PreparedStatement updateSupplier = connection.prepareStatement(
                         "UPDATE " + supplierTable + " SET \"supplierRemainig\" = \"supplierRemainig\" - ? WHERE \"supplierId\" = ?")) {

                insertReceipt.setInt(1, supplierReceipt.getTransId());
                insertReceipt.setBigDecimal(2, supplierReceipt.getAmountPaid());
                insertReceipt.setBigDecimal(3, supplierReceipt.getRemainingAmount());
                insertReceipt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                insertReceipt.setString(5, supplierReceipt.getUserRecived());
                insertReceipt.setInt(6, supplierReceipt.getSupplierId());
                insertReceipt.setString(7, supplierReceipt.getType());
                insertReceipt.setInt(8, supplierReceipt.getBranchId());
                insertReceipt.executeUpdate();

                updateInventory.setBigDecimal(1, supplierReceipt.getRemainingAmount());
                updateInventory.setInt(2, supplierReceipt.getTransId());
                int inventoryRows = updateInventory.executeUpdate();

                updateSupplier.setBigDecimal(1, supplierReceipt.getAmountPaid());
                updateSupplier.setInt(2, supplierReceipt.getSupplierId());
                int supplierRows = updateSupplier.executeUpdate();

                if (inventoryRows != 1 || supplierRows != 1) {
                    connection.rollback();
                    throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_RECEIPT_DEPENDENCY_NOT_FOUND",
                            "Supplier receipt references a missing transaction or supplier");
                }

                connection.commit();
                return "the Client Receipt Added Successfully : " + supplierReceipt.getSupplierId();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof ApiException) {
                    throw (ApiException) exception;
                }
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUPPLIER_RECEIPT_INSERT_FAILED",
                        "the ReceiptUser not added -> error in server!");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUPPLIER_RECEIPT_INSERT_FAILED",
                    "the ReceiptUser not added -> error in server!");
        }
    }
}
