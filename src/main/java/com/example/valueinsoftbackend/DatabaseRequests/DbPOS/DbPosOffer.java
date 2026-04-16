package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Offer;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class DbPosOffer {
    private final JdbcTemplate jdbcTemplate;

    public DbPosOffer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Offer> getOffers(int companyId, int branchId) {
        String table = TenantSqlIdentifiers.offerTable(companyId);
        String sql = "SELECT * FROM " + table + " WHERE branch_id = ? ORDER BY offer_id DESC";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Offer(
                rs.getInt("offer_id"),
                rs.getInt("branch_id"),
                rs.getString("offer_name"),
                rs.getString("offer_description"),
                rs.getString("offer_type"),
                rs.getDouble("offer_value"),
                rs.getDouble("min_order_total"),
                rs.getString("applicable_items"),
                rs.getInt("min_quantity"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("start_date"),
                rs.getTimestamp("end_date"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
        ), branchId);
    }

    public int saveOffer(Offer offer, int companyId) {
        String table = TenantSqlIdentifiers.offerTable(companyId);
        
        if (offer.getOfferId() > 0) {
            String sql = "UPDATE " + table + " SET offer_name = ?, offer_description = ?, offer_type = ?, offer_value = ?, " +
                    "min_order_total = ?, applicable_items = ?::jsonb, min_quantity = ?, is_active = ?, start_date = ?, end_date = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE offer_id = ? AND branch_id = ?";
            return jdbcTemplate.update(sql,
                    offer.getOfferName(),
                    offer.getOfferDescription(),
                    offer.getOfferType(),
                    offer.getOfferValue(),
                    offer.getMinOrderTotal(),
                    offer.getApplicableItems(),
                    offer.getMinQuantity(),
                    offer.isActive(),
                    offer.getStartDate(),
                    offer.getEndDate(),
                    offer.getOfferId(),
                    offer.getBranchId()
            );
        } else {
            String sql = "INSERT INTO " + table + " (branch_id, offer_name, offer_description, offer_type, offer_value, " +
                    "min_order_total, applicable_items, min_quantity, is_active, start_date, end_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING offer_id";
            Integer id = jdbcTemplate.queryForObject(sql, Integer.class,
                    offer.getBranchId(),
                    offer.getOfferName(),
                    offer.getOfferDescription(),
                    offer.getOfferType(),
                    offer.getOfferValue(),
                    offer.getMinOrderTotal(),
                    offer.getApplicableItems(),
                    offer.getMinQuantity(),
                    offer.isActive(),
                    offer.getStartDate(),
                    offer.getEndDate()
            );
            return id != null ? id : 0;
        }
    }

    public int deleteOffer(int offerId, int branchId, int companyId) {
        String table = TenantSqlIdentifiers.offerTable(companyId);
        String sql = "DELETE FROM " + table + " WHERE offer_id = ? AND branch_id = ?";
        return jdbcTemplate.update(sql, offerId, branchId);
    }
}
