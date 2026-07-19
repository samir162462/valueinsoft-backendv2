package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbPosInventoryTransactionHistoryQueryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void inventoryHistoryUsesLedgerRowsWithoutSyntheticNegativeTransactions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        DbPosInventoryTransaction repository = new DbPosInventoryTransaction(jdbcTemplate);

        repository.getInventoryTrans(1095, 3, "2026-07-01", "2026-07-31");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("-unit.product_unit_id"));
        assertFalse(sql.contains("UNION ALL"));
        assertTrue(sql.contains("FROM c_1095.inventory_stock_ledger ledger"));
        assertTrue(sql.contains("serialized_unit_reference"));
        assertTrue(sql.contains("reference_units.reference_id = ledger.reference_id"));
        assertArrayEquals(new Object[]{3, "2026-07-01", "2026-07-31"}, argsCaptor.getValue());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void purchaseHistoryIncludesReceiptLedgerAndNeverFabricatesEntryIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        DbPosInventoryTransaction repository = new DbPosInventoryTransaction(jdbcTemplate);

        repository.getProductPurchaseHistory(1095, 3, 1190, 25);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("-unit.product_unit_id"));
        assertFalse(sql.contains("UNION ALL"));
        assertTrue(sql.contains("'PURCHASE_RECEIPT'"));
        assertTrue(sql.contains("reference_units.reference_id = src.reference_id"));
        assertArrayEquals(new Object[]{3, 1190, 25}, argsCaptor.getValue());
    }
}
