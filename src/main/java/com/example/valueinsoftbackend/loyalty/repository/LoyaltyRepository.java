package com.example.valueinsoftbackend.loyalty.repository;

import com.example.valueinsoftbackend.loyalty.dto.LoyaltyAccountResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyLedgerItem;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyProgramConfig;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRecordedEarn;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRedemptionResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyReversalResult;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRewardResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LoyaltyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Set<Integer> readyTenants = ConcurrentHashMap.newKeySet();
    private final boolean isH2;

    public LoyaltyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        boolean checkH2 = false;
        try (java.sql.Connection conn = java.util.Objects.requireNonNull(jdbcTemplate.getJdbcTemplate().getDataSource()).getConnection()) {
            checkH2 = "H2".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
        } catch (Exception ignored) {
            // Default to false (production PG)
        }
        this.isH2 = checkH2;
    }

    public LoyaltyProgramConfig getEffectiveConfig(int companyId, int branchId) {
        ensureTables(companyId);
        String sql = """
                SELECT program_id, branch_id, status, points_name, earn_amount, earn_points, min_eligible_amount, expiry_months
                FROM %s
                WHERE branch_id = :branchId OR branch_id IS NULL
                ORDER BY branch_id NULLS LAST
                LIMIT 1
                """.formatted(configTable(companyId));

        List<LoyaltyProgramConfig> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("branchId", branchId),
                (rs, rowNum) -> new LoyaltyProgramConfig(
                        rs.getLong("program_id"),
                        (Integer) rs.getObject("branch_id"),
                        rs.getString("status"),
                        rs.getString("points_name"),
                        rs.getBigDecimal("earn_amount"),
                        rs.getInt("earn_points"),
                        rs.getBigDecimal("min_eligible_amount"),
                        (Integer) rs.getObject("expiry_months")
                ));
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        insertDefaultConfig(companyId);
        return getEffectiveConfig(companyId, branchId);
    }

    public LoyaltyAccountResponse getOrCreateAccount(int companyId, int branchId, int clientId) {
        ensureTables(companyId);
        ensureAccount(companyId, branchId, clientId);
        return findAccount(companyId, clientId);
    }

    public List<LoyaltyRewardResponse> listRewards(int companyId, int branchId, int clientId, BigDecimal orderNetAmount) {
        ensureTables(companyId);
        int availablePoints = 0;
        if (clientId > 0) {
            LoyaltyAccountResponse account = getOrCreateAccount(companyId, branchId, clientId);
            availablePoints = account == null ? 0 : account.availablePoints();
        }

        String sql = """
                SELECT reward_id, reward_name, reward_type, points_cost, discount_amount, minimum_spend
                FROM %s
                WHERE status = 'ACTIVE'
                  AND (branch_id = :branchId OR branch_id IS NULL)
                ORDER BY points_cost ASC, reward_id ASC
                """.formatted(rewardTable(companyId));
        BigDecimal net = orderNetAmount == null ? BigDecimal.ZERO : orderNetAmount.max(BigDecimal.ZERO);
        int finalAvailablePoints = availablePoints;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("branchId", branchId), (rs, rowNum) -> {
            int pointsCost = rs.getInt("points_cost");
            BigDecimal minimumSpend = rs.getBigDecimal("minimum_spend");
            BigDecimal discountAmount = rs.getBigDecimal("discount_amount");
            String reason = null;
            if (clientId <= 0) {
                reason = "Select a customer first.";
            } else if (finalAvailablePoints < pointsCost) {
                reason = "Customer does not have enough points.";
            } else if (net.compareTo(minimumSpend) < 0) {
                reason = "Order total is below reward minimum.";
            } else if (net.compareTo(discountAmount) < 0) {
                reason = "Reward discount is greater than order total.";
            }
            return new LoyaltyRewardResponse(
                    rs.getLong("reward_id"),
                    rs.getString("reward_name"),
                    rs.getString("reward_type"),
                    pointsCost,
                    discountAmount,
                    minimumSpend,
                    reason == null,
                    reason);
        });
    }

    public LoyaltyAccountResponse findAccount(int companyId, int clientId) {
        ensureTables(companyId);
        String sql;
        if (isH2) {
            sql = """
                SELECT account.loyalty_account_id,
                       account.client_id,
                       client."clientName" AS client_name,
                       client."clientPhone" AS client_phone,
                       account.status,
                       account.available_points,
                       account.pending_points,
                       account.lifetime_points,
                       account.redeemed_points,
                       account.expired_points,
                       account.tier_name,
                       account.last_activity_at,
                       (SELECT points_name FROM %3$s WHERE branch_id = account.branch_id OR branch_id IS NULL ORDER BY branch_id NULLS LAST LIMIT 1) AS points_name,
                       (SELECT earn_points FROM %3$s WHERE branch_id = account.branch_id OR branch_id IS NULL ORDER BY branch_id NULLS LAST LIMIT 1) AS earn_points,
                       (SELECT earn_amount FROM %3$s WHERE branch_id = account.branch_id OR branch_id IS NULL ORDER BY branch_id NULLS LAST LIMIT 1) AS earn_amount
                FROM %1$s account
                JOIN %2$s client ON client.c_id = account.client_id
                WHERE account.client_id = :clientId
                LIMIT 1
                """.formatted(accountTable(companyId), TenantSqlIdentifiers.clientTable(companyId), configTable(companyId));
        } else {
            sql = """
                SELECT account.loyalty_account_id,
                       account.client_id,
                       client."clientName" AS client_name,
                       client."clientPhone" AS client_phone,
                       account.status,
                       account.available_points,
                       account.pending_points,
                       account.lifetime_points,
                       account.redeemed_points,
                       account.expired_points,
                       account.tier_name,
                       account.last_activity_at,
                       config.points_name,
                       config.earn_points,
                       config.earn_amount
                FROM %s account
                JOIN %s client ON client.c_id = account.client_id
                CROSS JOIN LATERAL (
                    SELECT points_name, earn_points, earn_amount
                    FROM %s
                    WHERE branch_id = account.branch_id OR branch_id IS NULL
                    ORDER BY branch_id NULLS LAST
                    LIMIT 1
                ) config
                WHERE account.client_id = :clientId
                LIMIT 1
                """.formatted(accountTable(companyId), TenantSqlIdentifiers.clientTable(companyId), configTable(companyId));
        }

        List<LoyaltyAccountResponse> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("clientId", clientId),
                (rs, rowNum) -> new LoyaltyAccountResponse(
                        rs.getLong("loyalty_account_id"),
                        rs.getInt("client_id"),
                        rs.getString("client_name"),
                        rs.getString("client_phone"),
                        rs.getString("status"),
                        rs.getInt("available_points"),
                        rs.getInt("pending_points"),
                        rs.getInt("lifetime_points"),
                        rs.getInt("redeemed_points"),
                        rs.getInt("expired_points"),
                        rs.getString("tier_name"),
                        toLocalDateTime(rs.getTimestamp("last_activity_at")),
                        rs.getString("points_name"),
                        rs.getInt("earn_points"),
                        rs.getBigDecimal("earn_amount")
                ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<LoyaltyLedgerItem> listLedgerByClient(int companyId, int branchId, int clientId, int limit) {
        ensureTables(companyId);
        String sql = """
                SELECT ledger.ledger_id,
                       ledger.loyalty_account_id,
                       ledger.client_id,
                       ledger.branch_id,
                       ledger.movement_type,
                       CASE
                           WHEN ledger.movement_type = 'CONFIRM_REDEMPTION' THEN -COALESCE(redemption.reserved_points, 0)
                           WHEN ledger.movement_type IN ('RESERVE', 'RELEASE_RESERVATION') THEN 0
                           ELSE ledger.points_delta
                       END AS points_delta,
                       ledger.monetary_value,
                       ledger.source_type,
                       ledger.source_id,
                       ledger.order_id,
                       ledger.order_detail_id,
                       ledger.note,
                       ledger.created_by,
                       ledger.created_at
                FROM %s ledger
                LEFT JOIN %s redemption
                  ON ledger.source_type = 'LOYALTY_REDEMPTION'
                 AND ledger.source_id = redemption.redemption_id::text
                WHERE ledger.client_id = :clientId
                  AND ledger.branch_id = :branchId
                ORDER BY ledger.created_at DESC, ledger.ledger_id DESC
                LIMIT :limit
                """.formatted(ledgerTable(companyId), redemptionTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("clientId", clientId)
                        .addValue("branchId", branchId)
                        .addValue("limit", Math.max(1, Math.min(limit, 200))),
                (rs, rowNum) -> new LoyaltyLedgerItem(
                        rs.getLong("ledger_id"),
                        rs.getLong("loyalty_account_id"),
                        rs.getInt("client_id"),
                        rs.getInt("branch_id"),
                        rs.getString("movement_type"),
                        rs.getInt("points_delta"),
                        rs.getBigDecimal("monetary_value"),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        (Integer) rs.getObject("order_id"),
                        (Integer) rs.getObject("order_detail_id"),
                        rs.getString("note"),
                        rs.getString("created_by"),
                        toLocalDateTime(rs.getTimestamp("created_at"))));
    }

    public LoyaltyRecordedEarn recordOrderEarn(int companyId,
                                               int branchId,
                                               int clientId,
                                               int orderId,
                                               int points,
                                               BigDecimal monetaryValue,
                                               String actor,
                                               Integer expiryMonths) {
        ensureTables(companyId);
        ensureAccount(companyId, branchId, clientId);
        LoyaltyAccountResponse account = findAccount(companyId, clientId);
        if (account == null || points <= 0) {
            return new LoyaltyRecordedEarn(0, clientId, 0, false);
        }

        String idempotencyKey = "POS_ORDER_EARN:" + companyId + ":" + branchId + ":" + orderId;
        Timestamp expiresAt = expiryMonths == null ? null
                : Timestamp.valueOf(LocalDateTime.now().plusMonths(expiryMonths));
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, order_id, idempotency_key, note, created_by, expires_at, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'EARN', :points, :monetaryValue,
                    'POS_ORDER', :sourceId, :orderId, :idempotencyKey, :note, :actor,
                    :expiresAt,
                    NOW()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(ledgerTable(companyId));

        int inserted = jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", account.loyaltyAccountId())
                .addValue("clientId", clientId)
                .addValue("branchId", branchId)
                .addValue("points", points)
                .addValue("monetaryValue", monetaryValue == null ? BigDecimal.ZERO : monetaryValue)
                .addValue("sourceId", String.valueOf(orderId))
                .addValue("orderId", orderId)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("note", "Points earned from POS order #" + orderId)
                .addValue("actor", actor)
                .addValue("expiresAt", expiresAt, Types.TIMESTAMP));

        if (inserted == 1) {
            String updateSql = """
                    UPDATE %s
                    SET available_points = available_points + :points,
                        lifetime_points = lifetime_points + :points,
                        last_activity_at = NOW(),
                        updated_at = NOW()
                    WHERE loyalty_account_id = :accountId
                    """.formatted(accountTable(companyId));
            jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                    .addValue("points", points)
                    .addValue("accountId", account.loyaltyAccountId()));
        }

        return new LoyaltyRecordedEarn(account.loyaltyAccountId(), clientId, points, inserted == 1);
    }

    public LoyaltyRedemptionResponse reserveRedemption(int companyId,
                                                       int branchId,
                                                       int clientId,
                                                       Long rewardId,
                                                       BigDecimal orderNetAmount,
                                                       Integer requestedPoints,
                                                       BigDecimal requestedDiscountAmount,
                                                       String actor) {
        ensureTables(companyId);
        ensureAccount(companyId, branchId, clientId);
        AccountLock account = lockAccount(companyId, clientId);
        if (account == null) {
            throw new IllegalStateException("Loyalty account was not found.");
        }

        LoyaltyRewardResponse reward = resolveRewardForReservation(
                companyId,
                branchId,
                clientId,
                rewardId,
                orderNetAmount,
                account,
                requestedPoints,
                requestedDiscountAmount);
        String idempotencyKey = "LOYALTY_REDEMPTION:" + companyId + ":" + branchId + ":" + clientId + ":" + UUID.randomUUID();
        String redemptionSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, reward_id, status, reserved_points,
                    discount_amount, idempotency_key, created_by, reserved_at, expires_at
                ) VALUES (
                    :accountId, :clientId, :branchId, :rewardId, 'RESERVED', :points,
                    :discountAmount, :idempotencyKey, :actor, NOW(), NOW() + INTERVAL '15' MINUTE
                )
                """.formatted(redemptionTable(companyId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(redemptionSql, new MapSqlParameterSource()
                .addValue("accountId", account.loyaltyAccountId())
                .addValue("clientId", clientId)
                .addValue("branchId", branchId)
                .addValue("rewardId", reward.rewardId())
                .addValue("points", reward.pointsCost())
                .addValue("discountAmount", reward.discountAmount())
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("actor", actor), keyHolder, new String[]{"redemption_id"});
        long redemptionId = keyHolder.getKey().longValue();

        String ledgerKey = "LOYALTY_RESERVE:" + companyId + ":" + redemptionId;
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, idempotency_key, note, created_by, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'RESERVE', :pointsDelta, :discountAmount,
                    'LOYALTY_REDEMPTION', :sourceId, :idempotencyKey, :note, :actor, NOW()
                )
                """.formatted(ledgerTable(companyId));
        jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", account.loyaltyAccountId())
                .addValue("clientId", clientId)
                .addValue("branchId", branchId)
                .addValue("pointsDelta", -reward.pointsCost())
                .addValue("discountAmount", reward.discountAmount())
                .addValue("sourceId", String.valueOf(redemptionId))
                .addValue("idempotencyKey", ledgerKey)
                .addValue("note", "Reserved loyalty reward " + reward.rewardName())
                .addValue("actor", actor));

        String accountSql = """
                UPDATE %s
                SET available_points = available_points - :points,
                    pending_points = pending_points + :points,
                    updated_at = NOW()
                WHERE loyalty_account_id = :accountId
                  AND available_points >= :points
                """.formatted(accountTable(companyId));
        int updated = jdbcTemplate.update(accountSql, new MapSqlParameterSource()
                .addValue("points", reward.pointsCost())
                .addValue("accountId", account.loyaltyAccountId()));
        if (updated != 1) {
            throw new IllegalStateException("Customer does not have enough loyalty points.");
        }

        return new LoyaltyRedemptionResponse(
                redemptionId,
                reward.rewardId(),
                reward.rewardName(),
                "RESERVED",
                reward.pointsCost(),
                reward.discountAmount(),
                "Loyalty reward reserved.");
    }

    private LoyaltyRewardResponse resolveRewardForReservation(int companyId,
                                                              int branchId,
                                                              int clientId,
                                                              Long rewardId,
                                                              BigDecimal orderNetAmount,
                                                              AccountLock account,
                                                              Integer requestedPoints,
                                                              BigDecimal requestedDiscountAmount) {
        boolean hasCustomPoints = requestedPoints != null && requestedPoints > 0;
        boolean hasCustomDiscount = requestedDiscountAmount != null && requestedDiscountAmount.signum() > 0;
        if (hasCustomPoints || hasCustomDiscount) {
            if (!hasCustomPoints || !hasCustomDiscount) {
                throw new IllegalStateException("Both points and discount amount are required for custom redemption.");
            }
            BigDecimal discountAmount = requestedDiscountAmount.setScale(2, RoundingMode.UNNECESSARY);
            if (requestedPoints % 100 != 0) {
                throw new IllegalStateException("Loyalty points must be redeemed in 100 point blocks.");
            }
            if (discountAmount.remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException("Loyalty discount must be rounded down to 10 EGP blocks.");
            }
            if (BigDecimal.valueOf(requestedPoints).compareTo(discountAmount.multiply(BigDecimal.TEN)) != 0) {
                throw new IllegalStateException("Loyalty redemption must use 100 points for every 10 EGP discount.");
            }
            BigDecimal net = orderNetAmount == null ? BigDecimal.ZERO : orderNetAmount.max(BigDecimal.ZERO);
            if (net.compareTo(discountAmount) < 0) {
                throw new IllegalStateException("Reward discount is greater than order total.");
            }
            if (account.availablePoints() < requestedPoints) {
                throw new IllegalStateException("Customer does not have enough loyalty points.");
            }
            long generatedRewardId = findOrCreateExactReward(companyId, branchId, requestedPoints, discountAmount);
            String rewardName = "Redeem max: " + requestedPoints + " pts = " + discountAmount.stripTrailingZeros().toPlainString() + " EGP";
            return new LoyaltyRewardResponse(
                    generatedRewardId,
                    rewardName,
                    "FIXED_DISCOUNT",
                    requestedPoints,
                    discountAmount,
                    BigDecimal.ZERO,
                    true,
                    null);
        }

        List<LoyaltyRewardResponse> rewards = listRewards(companyId, branchId, clientId, orderNetAmount).stream()
                .filter(LoyaltyRewardResponse::eligible)
                .filter(reward -> rewardId == null || reward.rewardId() == rewardId)
                .sorted(Comparator.comparingInt(LoyaltyRewardResponse::pointsCost).reversed())
                .toList();
        if (rewards.isEmpty()) {
            throw new IllegalStateException("No eligible loyalty reward is available.");
        }
        return rewards.get(0);
    }

    private long findOrCreateExactReward(int companyId, int branchId, int pointsCost, BigDecimal discountAmount) {
        String selectSql = """
                SELECT reward_id
                FROM %s
                WHERE branch_id = :branchId
                  AND reward_type = 'FIXED_DISCOUNT'
                  AND points_cost = :pointsCost
                  AND discount_amount = :discountAmount
                  AND status = 'ACTIVE'
                ORDER BY reward_id ASC
                LIMIT 1
                """.formatted(rewardTable(companyId));
        List<Long> existing = jdbcTemplate.query(
                selectSql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("pointsCost", pointsCost)
                        .addValue("discountAmount", discountAmount),
                (rs, rowNum) -> rs.getLong("reward_id"));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String rewardName = "Redeem max: " + pointsCost + " pts = " + discountAmount.stripTrailingZeros().toPlainString() + " EGP";
        String insertSql = """
                INSERT INTO %s (branch_id, reward_name, reward_type, points_cost, discount_amount, minimum_spend, status, created_at, updated_at)
                VALUES (:branchId, :rewardName, 'FIXED_DISCOUNT', :pointsCost, :discountAmount, 0, 'ACTIVE', NOW(), NOW())
                RETURNING reward_id
                """.formatted(rewardTable(companyId));
        Long rewardId = jdbcTemplate.queryForObject(insertSql, new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("rewardName", rewardName)
                .addValue("pointsCost", pointsCost)
                .addValue("discountAmount", discountAmount), Long.class);
        if (rewardId == null) {
            throw new IllegalStateException("Loyalty reward was not created.");
        }
        return rewardId;
    }

    public LoyaltyRedemptionResponse confirmRedemption(int companyId,
                                                       int branchId,
                                                       int clientId,
                                                       long redemptionId,
                                                       int orderId,
                                                       String actor) {
        ensureTables(companyId);
        RedemptionLock redemption = lockRedemption(companyId, redemptionId);
        if (redemption == null) {
            throw new IllegalStateException("Loyalty redemption was not found.");
        }
        if (redemption.branchId() != branchId) {
            throw new IllegalStateException("Loyalty redemption belongs to a different branch.");
        }
        if (clientId <= 0 || redemption.clientId() != clientId) {
            throw new IllegalStateException("Loyalty redemption belongs to a different customer.");
        }
        if (!"RESERVED".equals(redemption.status())) {
            return redemption.toResponse("Loyalty redemption is already " + redemption.status().toLowerCase() + ".");
        }

        String updateSql = """
                UPDATE %s
                SET status = 'CONFIRMED',
                    order_id = :orderId,
                    confirmed_at = NOW()
                WHERE redemption_id = :redemptionId
                  AND status = 'RESERVED'
                """.formatted(redemptionTable(companyId));
        jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("redemptionId", redemptionId));

        String accountSql = """
                UPDATE %s
                SET pending_points = GREATEST(0, pending_points - :points),
                    redeemed_points = redeemed_points + :points,
                    last_activity_at = NOW(),
                    updated_at = NOW()
                WHERE loyalty_account_id = :accountId
                """.formatted(accountTable(companyId));
        jdbcTemplate.update(accountSql, new MapSqlParameterSource()
                .addValue("points", redemption.points())
                .addValue("accountId", redemption.accountId()));

        String ledgerKey = "LOYALTY_CONFIRM:" + companyId + ":" + redemptionId + ":" + orderId;
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, order_id, idempotency_key, note, created_by, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'CONFIRM_REDEMPTION', 0, :discountAmount,
                    'LOYALTY_REDEMPTION', :sourceId, :orderId, :idempotencyKey, :note, :actor, NOW()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(ledgerTable(companyId));
        jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", redemption.accountId())
                .addValue("clientId", redemption.clientId())
                .addValue("branchId", branchId)
                .addValue("discountAmount", redemption.discountAmount())
                .addValue("sourceId", String.valueOf(redemptionId))
                .addValue("orderId", orderId)
                .addValue("idempotencyKey", ledgerKey)
                .addValue("note", "Confirmed loyalty redemption on POS order #" + orderId)
                .addValue("actor", actor));

        return redemption.withStatus("CONFIRMED").toResponse("Loyalty reward confirmed.");
    }

    public LoyaltyRedemptionResponse releaseRedemption(int companyId, int branchId, long redemptionId, String actor) {
        ensureTables(companyId);
        RedemptionLock redemption = lockRedemption(companyId, redemptionId);
        if (redemption == null) {
            throw new IllegalStateException("Loyalty redemption was not found.");
        }
        if (redemption.branchId() != branchId) {
            throw new IllegalStateException("Loyalty redemption belongs to a different branch.");
        }
        if (!"RESERVED".equals(redemption.status())) {
            return redemption.toResponse("Loyalty redemption is already " + redemption.status().toLowerCase() + ".");
        }

        String updateSql = """
                UPDATE %s
                SET status = 'RELEASED',
                    released_at = NOW()
                WHERE redemption_id = :redemptionId
                  AND status = 'RESERVED'
                """.formatted(redemptionTable(companyId));
        jdbcTemplate.update(updateSql, new MapSqlParameterSource("redemptionId", redemptionId));

        String accountSql = """
                UPDATE %s
                SET available_points = available_points + :points,
                    pending_points = GREATEST(0, pending_points - :points),
                    updated_at = NOW()
                WHERE loyalty_account_id = :accountId
                """.formatted(accountTable(companyId));
        jdbcTemplate.update(accountSql, new MapSqlParameterSource()
                .addValue("points", redemption.points())
                .addValue("accountId", redemption.accountId()));

        String ledgerKey = "LOYALTY_RELEASE:" + companyId + ":" + redemptionId;
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, idempotency_key, note, created_by, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'RELEASE_RESERVATION', :points, :discountAmount,
                    'LOYALTY_REDEMPTION', :sourceId, :idempotencyKey, :note, :actor, NOW()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(ledgerTable(companyId));
        jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", redemption.accountId())
                .addValue("clientId", redemption.clientId())
                .addValue("branchId", redemption.branchId())
                .addValue("points", redemption.points())
                .addValue("discountAmount", redemption.discountAmount())
                .addValue("sourceId", String.valueOf(redemptionId))
                .addValue("idempotencyKey", ledgerKey)
                .addValue("note", "Released loyalty redemption reservation")
                .addValue("actor", actor));

        return redemption.withStatus("RELEASED").toResponse("Loyalty reward released.");
    }

    public LoyaltyReversalResult reverseForReturn(int companyId,
                                                  int branchId,
                                                  int clientId,
                                                  int orderId,
                                                  int orderDetailId,
                                                  BigDecimal refundAmount,
                                                  BigDecimal originalOrderTotal,
                                                  boolean fullReturn,
                                                  String actor) {
        ensureTables(companyId);
        if (clientId <= 0 || orderId <= 0 || orderDetailId <= 0) {
            return new LoyaltyReversalResult(0, 0, false);
        }
        AccountLock account = lockAccount(companyId, clientId);
        if (account == null) {
            return new LoyaltyReversalResult(0, 0, false);
        }

        int earnedReversed = reverseEarnedPoints(
                companyId, branchId, clientId, orderId, orderDetailId, refundAmount, originalOrderTotal, fullReturn, actor, account);
        int redeemedRestored = restoreRedeemedPoints(
                companyId, branchId, clientId, orderId, orderDetailId, refundAmount, originalOrderTotal, fullReturn, actor);

        return new LoyaltyReversalResult(earnedReversed, redeemedRestored, earnedReversed > 0 || redeemedRestored > 0);
    }

    private int reverseEarnedPoints(int companyId,
                                    int branchId,
                                    int clientId,
                                    int orderId,
                                    int orderDetailId,
                                    BigDecimal refundAmount,
                                    BigDecimal originalOrderTotal,
                                    boolean fullReturn,
                                    String actor,
                                    AccountLock account) {
        String sql = """
                SELECT loyalty_account_id, points_delta, monetary_value
                FROM %s
                WHERE movement_type = 'EARN'
                  AND branch_id = :branchId
                  AND order_id = :orderId
                  AND points_delta > 0
                ORDER BY ledger_id ASC
                LIMIT 1
                """.formatted(ledgerTable(companyId));
        List<EarnLedger> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("orderId", orderId),
                (rs, rowNum) -> new EarnLedger(
                        rs.getLong("loyalty_account_id"),
                        rs.getInt("points_delta"),
                        rs.getBigDecimal("monetary_value")));
        if (rows.isEmpty()) {
            return 0;
        }

        EarnLedger earned = rows.get(0);
        int pointsToReverse = proportionalPoints(earned.points(), refundAmount, originalOrderTotal, fullReturn);
        if (pointsToReverse <= 0) {
            return 0;
        }

        String idempotencyKey = "LOYALTY_EARN_RETURN:" + companyId + ":" + branchId + ":" + orderDetailId;
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, order_id, order_detail_id, idempotency_key, note, created_by, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'RETURN_REVERSAL', :pointsDelta, :refundAmount,
                    'ORDER_RETURN', :sourceId, :orderId, :orderDetailId, :idempotencyKey, :note, :actor, NOW()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(ledgerTable(companyId));
        int inserted = jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", earned.accountId())
                .addValue("clientId", clientId)
                .addValue("branchId", branchId)
                .addValue("pointsDelta", -pointsToReverse)
                .addValue("refundAmount", refundAmount)
                .addValue("sourceId", String.valueOf(orderDetailId))
                .addValue("orderId", orderId)
                .addValue("orderDetailId", orderDetailId)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("note", "Reversed earned loyalty points for returned order detail #" + orderDetailId)
                .addValue("actor", actor));
        if (inserted != 1) {
            return 0;
        }

        String updateSql = """
                UPDATE %s
                SET available_points = GREATEST(0, available_points - :points),
                    lifetime_points = GREATEST(0, lifetime_points - :points),
                    updated_at = NOW()
                WHERE loyalty_account_id = :accountId
                """.formatted(accountTable(companyId));
        jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                .addValue("points", pointsToReverse)
                .addValue("accountId", account.loyaltyAccountId()));
        return pointsToReverse;
    }

    private int restoreRedeemedPoints(int companyId,
                                      int branchId,
                                      int clientId,
                                      int orderId,
                                      int orderDetailId,
                                      BigDecimal refundAmount,
                                      BigDecimal originalOrderTotal,
                                      boolean fullReturn,
                                      String actor) {
        String sql = """
                SELECT redemption_id, loyalty_account_id, reward_id, reward_name, reserved_points, discount_amount
                FROM (
                    SELECT r.redemption_id,
                           r.loyalty_account_id,
                           r.reward_id,
                           reward.reward_name,
                           r.reserved_points,
                           r.discount_amount
                    FROM %s r
                    JOIN %s reward ON reward.reward_id = r.reward_id
                    WHERE r.branch_id = :branchId
                      AND r.client_id = :clientId
                      AND r.order_id = :orderId
                      AND r.status = 'CONFIRMED'
                    ORDER BY r.redemption_id ASC
                    LIMIT 1
                ) redemption
                """.formatted(redemptionTable(companyId), rewardTable(companyId));
        List<ConfirmedRedemption> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("clientId", clientId)
                        .addValue("orderId", orderId),
                (rs, rowNum) -> new ConfirmedRedemption(
                        rs.getLong("redemption_id"),
                        rs.getLong("loyalty_account_id"),
                        rs.getLong("reward_id"),
                        rs.getString("reward_name"),
                        rs.getInt("reserved_points"),
                        rs.getBigDecimal("discount_amount")));
        if (rows.isEmpty()) {
            return 0;
        }

        ConfirmedRedemption redemption = rows.get(0);
        int pointsToRestore = proportionalPoints(redemption.points(), refundAmount, originalOrderTotal, fullReturn);
        if (pointsToRestore <= 0) {
            return 0;
        }

        String idempotencyKey = "LOYALTY_REDEEM_RETURN:" + companyId + ":" + branchId + ":" + orderDetailId;
        String ledgerSql = """
                INSERT INTO %s (
                    loyalty_account_id, client_id, branch_id, movement_type, points_delta, monetary_value,
                    source_type, source_id, order_id, order_detail_id, idempotency_key, note, created_by, created_at
                ) VALUES (
                    :accountId, :clientId, :branchId, 'RETURN_REVERSAL', :points, :refundAmount,
                    'LOYALTY_REDEMPTION_RETURN', :sourceId, :orderId, :orderDetailId, :idempotencyKey, :note, :actor, NOW()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """.formatted(ledgerTable(companyId));
        int inserted = jdbcTemplate.update(ledgerSql, new MapSqlParameterSource()
                .addValue("accountId", redemption.accountId())
                .addValue("clientId", clientId)
                .addValue("branchId", branchId)
                .addValue("points", pointsToRestore)
                .addValue("refundAmount", refundAmount)
                .addValue("sourceId", String.valueOf(redemption.redemptionId()))
                .addValue("orderId", orderId)
                .addValue("orderDetailId", orderDetailId)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("note", "Restored redeemed loyalty points for returned order detail #" + orderDetailId)
                .addValue("actor", actor));
        if (inserted != 1) {
            return 0;
        }

        String accountSql = """
                UPDATE %s
                SET available_points = available_points + :points,
                    redeemed_points = GREATEST(0, redeemed_points - :points),
                    updated_at = NOW()
                WHERE loyalty_account_id = :accountId
                """.formatted(accountTable(companyId));
        jdbcTemplate.update(accountSql, new MapSqlParameterSource()
                .addValue("points", pointsToRestore)
                .addValue("accountId", redemption.accountId()));
        return pointsToRestore;
    }

    private void ensureAccount(int companyId, int branchId, int clientId) {
        if (isH2) {
            String checkSql = "SELECT COUNT(*) FROM " + accountTable(companyId) + " WHERE client_id = :clientId";
            Integer count = jdbcTemplate.queryForObject(checkSql, new MapSqlParameterSource("clientId", clientId), Integer.class);
            if (count == null || count == 0) {
                String insertSql = "INSERT INTO " + accountTable(companyId) + " (client_id, branch_id, phone_normalized, status, created_at, updated_at) " +
                        "SELECT c_id, :branchId, REGEXP_REPLACE(COALESCE(\"clientPhone\", ''), '[^0-9+]', ''), 'ACTIVE', NOW(), NOW() " +
                        "FROM " + TenantSqlIdentifiers.clientTable(companyId) + " WHERE c_id = :clientId";
                jdbcTemplate.update(insertSql, new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("clientId", clientId));
            }
        } else {
            String sql = """
                    INSERT INTO %s (client_id, branch_id, phone_normalized, status, created_at, updated_at)
                    SELECT c_id, :branchId, regexp_replace(COALESCE("clientPhone", ''), '[^0-9+]', '', 'g'), 'ACTIVE', NOW(), NOW()
                    FROM %s
                    WHERE c_id = :clientId
                    ON CONFLICT (client_id) DO NOTHING
                    """.formatted(accountTable(companyId), TenantSqlIdentifiers.clientTable(companyId));
            jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("branchId", branchId)
                    .addValue("clientId", clientId));
        }
    }

    private void insertDefaultConfig(int companyId) {
        String sql = """
                INSERT INTO %s (branch_id, status, points_name, earn_amount, earn_points, min_eligible_amount, created_at, updated_at)
                VALUES (NULL, 'ACTIVE', 'Points', 10, 1, 1, NOW(), NOW())
                ON CONFLICT DO NOTHING
                """.formatted(configTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource());
    }

    private void ensureTables(int companyId) {
        if (readyTenants.contains(companyId)) {
            return;
        }
        synchronized (readyTenants) {
            if (readyTenants.contains(companyId)) {
                return;
            }
            String schema = TenantSqlIdentifiers.companySchema(companyId);
            jdbcTemplate.getJdbcOperations().execute(createConfigSql(schema));
            jdbcTemplate.getJdbcOperations().execute(createAccountSql(schema));
            jdbcTemplate.getJdbcOperations().execute(createLedgerSql(schema));
            jdbcTemplate.getJdbcOperations().execute(createRewardSql(schema));
            jdbcTemplate.getJdbcOperations().execute(createRedemptionSql(schema));
            insertDefaultConfig(companyId);
            insertDefaultReward(companyId);
            readyTenants.add(companyId);
        }
    }

    private String createConfigSql(String schema) {
        String indexSql = isH2
                ? "CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_program_branch_unique ON " + schema + ".loyalty_program_config (branch_id);"
                : "CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_program_branch_unique ON " + schema + ".loyalty_program_config ((COALESCE(branch_id, 0)));";

        return "CREATE TABLE IF NOT EXISTS " + schema + ".loyalty_program_config (" +
                " program_id BIGSERIAL PRIMARY KEY," +
                " branch_id INTEGER," +
                " status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                " points_name VARCHAR(40) NOT NULL DEFAULT 'Points'," +
                " earn_amount NUMERIC(14,2) NOT NULL DEFAULT 10," +
                " earn_points INTEGER NOT NULL DEFAULT 1," +
                " min_eligible_amount NUMERIC(14,2) NOT NULL DEFAULT 1," +
                " expiry_months INTEGER," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT loyalty_program_config_status_ck CHECK (status IN ('ACTIVE','PAUSED','DISABLED'))," +
                " CONSTRAINT loyalty_program_config_earn_amount_ck CHECK (earn_amount > 0)," +
                " CONSTRAINT loyalty_program_config_earn_points_ck CHECK (earn_points > 0)," +
                " CONSTRAINT loyalty_program_config_min_amount_ck CHECK (min_eligible_amount >= 0)," +
                " CONSTRAINT loyalty_program_config_expiry_ck CHECK (expiry_months IS NULL OR expiry_months > 0)" +
                "); " +
                indexSql;
    }

    private String createAccountSql(String schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".loyalty_account (" +
                " loyalty_account_id BIGSERIAL PRIMARY KEY," +
                " client_id INTEGER NOT NULL," +
                " branch_id INTEGER," +
                " phone_normalized VARCHAR(40)," +
                " status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                " available_points INTEGER NOT NULL DEFAULT 0," +
                " pending_points INTEGER NOT NULL DEFAULT 0," +
                " lifetime_points INTEGER NOT NULL DEFAULT 0," +
                " redeemed_points INTEGER NOT NULL DEFAULT 0," +
                " expired_points INTEGER NOT NULL DEFAULT 0," +
                " tier_name VARCHAR(40) NOT NULL DEFAULT 'Base'," +
                " last_activity_at TIMESTAMP," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT loyalty_account_client_fk FOREIGN KEY (client_id) REFERENCES " + schema + ".\"Client\" (c_id) ON DELETE CASCADE," +
                " CONSTRAINT loyalty_account_status_ck CHECK (status IN ('ACTIVE','PAUSED','BLOCKED'))," +
                " CONSTRAINT loyalty_account_points_ck CHECK (available_points >= 0 AND pending_points >= 0 AND lifetime_points >= 0 AND redeemed_points >= 0 AND expired_points >= 0)" +
                "); " +
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_account_client ON " + schema + ".loyalty_account (client_id); " +
                "CREATE INDEX IF NOT EXISTS idx_loyalty_account_branch ON " + schema + ".loyalty_account (branch_id, last_activity_at DESC);";
    }

    private String createLedgerSql(String schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".loyalty_ledger (" +
                " ledger_id BIGSERIAL PRIMARY KEY," +
                " loyalty_account_id BIGINT NOT NULL," +
                " client_id INTEGER NOT NULL," +
                " branch_id INTEGER NOT NULL," +
                " movement_type VARCHAR(40) NOT NULL," +
                " points_delta INTEGER NOT NULL," +
                " monetary_value NUMERIC(14,2) NOT NULL DEFAULT 0," +
                " source_type VARCHAR(40) NOT NULL," +
                " source_id VARCHAR(120) NOT NULL," +
                " order_id INTEGER," +
                " order_detail_id INTEGER," +
                " idempotency_key VARCHAR(200) NOT NULL," +
                " note VARCHAR(500)," +
                " created_by VARCHAR(120)," +
                " expires_at TIMESTAMP," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT loyalty_ledger_account_fk FOREIGN KEY (loyalty_account_id) REFERENCES " + schema + ".loyalty_account (loyalty_account_id) ON DELETE CASCADE," +
                " CONSTRAINT loyalty_ledger_client_fk FOREIGN KEY (client_id) REFERENCES " + schema + ".\"Client\" (c_id) ON DELETE CASCADE," +
                " CONSTRAINT loyalty_ledger_type_ck CHECK (movement_type IN ('EARN','REDEEM','RESERVE','RELEASE_RESERVATION','CONFIRM_REDEMPTION','RETURN_REVERSAL','VOID_REVERSAL','EXPIRE','MANUAL_ADJUST','MIGRATION'))" +
                "); " +
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_ledger_idempotency ON " + schema + ".loyalty_ledger (idempotency_key); " +
                "CREATE INDEX IF NOT EXISTS idx_loyalty_ledger_account_time ON " + schema + ".loyalty_ledger (loyalty_account_id, created_at DESC); " +
                "CREATE INDEX IF NOT EXISTS idx_loyalty_ledger_order ON " + schema + ".loyalty_ledger (branch_id, order_id);";
    }

    private String createRewardSql(String schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".loyalty_reward (" +
                " reward_id BIGSERIAL PRIMARY KEY," +
                " branch_id INTEGER," +
                " reward_name VARCHAR(120) NOT NULL," +
                " reward_type VARCHAR(40) NOT NULL DEFAULT 'FIXED_DISCOUNT'," +
                " points_cost INTEGER NOT NULL," +
                " discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0," +
                " minimum_spend NUMERIC(14,2) NOT NULL DEFAULT 0," +
                " status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " CONSTRAINT loyalty_reward_type_ck CHECK (reward_type IN ('FIXED_DISCOUNT'))," +
                " CONSTRAINT loyalty_reward_status_ck CHECK (status IN ('ACTIVE','PAUSED','DISABLED'))," +
                " CONSTRAINT loyalty_reward_points_ck CHECK (points_cost > 0)," +
                " CONSTRAINT loyalty_reward_amount_ck CHECK (discount_amount > 0 AND minimum_spend >= 0)" +
                "); " +
                "CREATE INDEX IF NOT EXISTS idx_loyalty_reward_branch_status ON " + schema + ".loyalty_reward (branch_id, status, points_cost);";
    }

    private String createRedemptionSql(String schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".loyalty_redemption (" +
                " redemption_id BIGSERIAL PRIMARY KEY," +
                " loyalty_account_id BIGINT NOT NULL," +
                " client_id INTEGER NOT NULL," +
                " branch_id INTEGER NOT NULL," +
                " reward_id BIGINT NOT NULL," +
                " status VARCHAR(30) NOT NULL DEFAULT 'RESERVED'," +
                " reserved_points INTEGER NOT NULL," +
                " discount_amount NUMERIC(14,2) NOT NULL," +
                " order_id INTEGER," +
                " idempotency_key VARCHAR(200) NOT NULL," +
                " created_by VARCHAR(120)," +
                " reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                " expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '15' MINUTE)," +
                " confirmed_at TIMESTAMP," +
                " released_at TIMESTAMP," +
                " CONSTRAINT loyalty_redemption_account_fk FOREIGN KEY (loyalty_account_id) REFERENCES " + schema + ".loyalty_account (loyalty_account_id) ON DELETE CASCADE," +
                " CONSTRAINT loyalty_redemption_client_fk FOREIGN KEY (client_id) REFERENCES " + schema + ".\"Client\" (c_id) ON DELETE CASCADE," +
                " CONSTRAINT loyalty_redemption_reward_fk FOREIGN KEY (reward_id) REFERENCES " + schema + ".loyalty_reward (reward_id)," +
                " CONSTRAINT loyalty_redemption_status_ck CHECK (status IN ('RESERVED','CONFIRMED','RELEASED','EXPIRED','FAILED'))," +
                " CONSTRAINT loyalty_redemption_values_ck CHECK (reserved_points > 0 AND discount_amount > 0)" +
                "); " +
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_redemption_idempotency ON " + schema + ".loyalty_redemption (idempotency_key); " +
                "CREATE INDEX IF NOT EXISTS idx_loyalty_redemption_account_status ON " + schema + ".loyalty_redemption (loyalty_account_id, status, reserved_at DESC);";
    }

    public void updateConfig(int companyId, LoyaltyProgramConfig config) {
        ensureTables(companyId);
        String checkSql = "SELECT COUNT(*) FROM " + configTable(companyId) + " WHERE (branch_id = :branchId) OR (branch_id IS NULL AND CAST(:branchId AS INTEGER) IS NULL)";
        Integer count = jdbcTemplate.queryForObject(checkSql, new MapSqlParameterSource().addValue("branchId", config.branchId(), Types.INTEGER), Integer.class);
        
        if (count != null && count > 0) {
            String updateSql = """
                    UPDATE %s
                    SET status = :status,
                        points_name = :pointsName,
                        earn_amount = :earnAmount,
                        earn_points = :earnPoints,
                        min_eligible_amount = :minEligibleAmount,
                        expiry_months = :expiryMonths,
                        updated_at = NOW()
                    WHERE (branch_id = :branchId) OR (branch_id IS NULL AND CAST(:branchId AS INTEGER) IS NULL)
                    """.formatted(configTable(companyId));
            jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                    .addValue("status", config.status())
                    .addValue("pointsName", config.pointsName())
                    .addValue("earnAmount", config.earnAmount())
                    .addValue("earnPoints", config.earnPoints())
                    .addValue("minEligibleAmount", config.minEligibleAmount())
                    .addValue("expiryMonths", config.expiryMonths())
                    .addValue("branchId", config.branchId(), Types.INTEGER));
        } else {
            String insertSql = """
                    INSERT INTO %s (branch_id, status, points_name, earn_amount, earn_points, min_eligible_amount, expiry_months, created_at, updated_at)
                    VALUES (:branchId, :status, :pointsName, :earnAmount, :earnPoints, :minEligibleAmount, :expiryMonths, NOW(), NOW())
                    """.formatted(configTable(companyId));
            jdbcTemplate.update(insertSql, new MapSqlParameterSource()
                    .addValue("branchId", config.branchId())
                    .addValue("status", config.status())
                    .addValue("pointsName", config.pointsName())
                    .addValue("earnAmount", config.earnAmount())
                    .addValue("earnPoints", config.earnPoints())
                    .addValue("minEligibleAmount", config.minEligibleAmount())
                    .addValue("expiryMonths", config.expiryMonths()));
        }
    }

    public List<LoyaltyRewardResponse> listAllRewards(int companyId, Integer branchId) {
        ensureTables(companyId);
        String sql = """
                SELECT reward_id, reward_name, reward_type, points_cost, discount_amount, minimum_spend, status
                FROM %s
                WHERE (branch_id = :branchId OR branch_id IS NULL) AND status <> 'DISABLED'
                ORDER BY points_cost ASC, reward_id ASC
                """.formatted(rewardTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource("branchId", branchId), (rs, rowNum) -> {
            return new LoyaltyRewardResponse(
                    rs.getLong("reward_id"),
                    rs.getString("reward_name"),
                    rs.getString("reward_type"),
                    rs.getInt("points_cost"),
                    rs.getBigDecimal("discount_amount"),
                    rs.getBigDecimal("minimum_spend"),
                    "ACTIVE".equalsIgnoreCase(rs.getString("status")),
                    rs.getString("status")
            );
        });
    }

    public void createReward(int companyId, LoyaltyRewardResponse reward, Integer branchId) {
        ensureTables(companyId);
        String sql = """
                INSERT INTO %s (branch_id, reward_name, reward_type, points_cost, discount_amount, minimum_spend, status, created_at, updated_at)
                VALUES (:branchId, :rewardName, :rewardType, :pointsCost, :discountAmount, :minimumSpend, :status, NOW(), NOW())
                """.formatted(rewardTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("rewardName", reward.rewardName())
                .addValue("rewardType", reward.rewardType() != null ? reward.rewardType() : "FIXED_DISCOUNT")
                .addValue("pointsCost", reward.pointsCost())
                .addValue("discountAmount", reward.discountAmount())
                .addValue("minimumSpend", reward.minimumSpend() != null ? reward.minimumSpend() : BigDecimal.ZERO)
                .addValue("status", reward.eligible() ? "ACTIVE" : "PAUSED"));
    }

    public void updateReward(int companyId, LoyaltyRewardResponse reward) {
        ensureTables(companyId);
        String sql = """
                UPDATE %s
                SET reward_name = :rewardName,
                    points_cost = :pointsCost,
                    discount_amount = :discountAmount,
                    minimum_spend = :minimumSpend,
                    status = :status,
                    updated_at = NOW()
                WHERE reward_id = :rewardId
                """.formatted(rewardTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("rewardName", reward.rewardName())
                .addValue("pointsCost", reward.pointsCost())
                .addValue("discountAmount", reward.discountAmount())
                .addValue("minimumSpend", reward.minimumSpend() != null ? reward.minimumSpend() : BigDecimal.ZERO)
                .addValue("status", reward.eligible() ? "ACTIVE" : "PAUSED")
                .addValue("rewardId", reward.rewardId()));
    }

    public void deleteReward(int companyId, long rewardId) {
        ensureTables(companyId);
        String sql = "UPDATE " + rewardTable(companyId) + " SET status = 'DISABLED', updated_at = NOW() WHERE reward_id = :rewardId";
        jdbcTemplate.update(sql, new MapSqlParameterSource("rewardId", rewardId));
    }

    private void insertDefaultReward(int companyId) {
        String sql = """
                INSERT INTO %s (branch_id, reward_name, reward_type, points_cost, discount_amount, minimum_spend, status, created_at, updated_at)
                SELECT NULL, '100 Points = 10 LE', 'FIXED_DISCOUNT', 100, 10, 0, 'ACTIVE', NOW(), NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM %s WHERE branch_id IS NULL AND reward_type = 'FIXED_DISCOUNT'
                )
                """.formatted(rewardTable(companyId), rewardTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource());
    }

    private String configTable(int companyId) {
        return TenantSqlIdentifiers.loyaltyProgramConfigTable(companyId);
    }

    private String accountTable(int companyId) {
        return TenantSqlIdentifiers.loyaltyAccountTable(companyId);
    }

    private String ledgerTable(int companyId) {
        return TenantSqlIdentifiers.loyaltyLedgerTable(companyId);
    }

    private String rewardTable(int companyId) {
        return TenantSqlIdentifiers.loyaltyRewardTable(companyId);
    }

    private String redemptionTable(int companyId) {
        return TenantSqlIdentifiers.loyaltyRedemptionTable(companyId);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private int proportionalPoints(int totalPoints, BigDecimal refundAmount, BigDecimal originalOrderTotal, boolean fullReturn) {
        if (totalPoints <= 0) {
            return 0;
        }
        if (fullReturn || originalOrderTotal == null || originalOrderTotal.signum() <= 0) {
            return totalPoints;
        }
        BigDecimal refund = refundAmount == null ? BigDecimal.ZERO : refundAmount.max(BigDecimal.ZERO);
        if (refund.signum() <= 0) {
            return 0;
        }
        BigDecimal ratio = refund.divide(originalOrderTotal, 8, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        int calculated = ratio.multiply(BigDecimal.valueOf(totalPoints)).setScale(0, RoundingMode.DOWN).intValue();
        return Math.min(totalPoints, Math.max(1, calculated));
    }

    private AccountLock lockAccount(int companyId, int clientId) {
        String sql = """
                SELECT loyalty_account_id, available_points
                FROM %s
                WHERE client_id = :clientId
                FOR UPDATE
                """.formatted(accountTable(companyId));
        List<AccountLock> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("clientId", clientId),
                (rs, rowNum) -> new AccountLock(rs.getLong("loyalty_account_id"), rs.getInt("available_points")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RedemptionLock lockRedemption(int companyId, long redemptionId) {
        String sql = """
                SELECT redemption.redemption_id,
                       redemption.loyalty_account_id,
                       redemption.client_id,
                       redemption.branch_id,
                       redemption.reward_id,
                       reward.reward_name,
                       redemption.status,
                       redemption.reserved_points,
                       redemption.discount_amount
                FROM %s redemption
                JOIN %s reward ON reward.reward_id = redemption.reward_id
                WHERE redemption.redemption_id = :redemptionId
                FOR UPDATE
                """.formatted(redemptionTable(companyId), rewardTable(companyId));
        List<RedemptionLock> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("redemptionId", redemptionId),
                (rs, rowNum) -> new RedemptionLock(
                        rs.getLong("redemption_id"),
                        rs.getLong("loyalty_account_id"),
                        rs.getInt("client_id"),
                        rs.getInt("branch_id"),
                        rs.getLong("reward_id"),
                        rs.getString("reward_name"),
                        rs.getString("status"),
                        rs.getInt("reserved_points"),
                        rs.getBigDecimal("discount_amount")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record AccountLock(long loyaltyAccountId, int availablePoints) {
    }

    private record RedemptionLock(long redemptionId,
                                  long accountId,
                                  int clientId,
                                  int branchId,
                                  long rewardId,
                                  String rewardName,
                                  String status,
                                  int points,
                                  BigDecimal discountAmount) {
        RedemptionLock withStatus(String newStatus) {
            return new RedemptionLock(redemptionId, accountId, clientId, branchId, rewardId, rewardName,
                    newStatus, points, discountAmount);
        }

        LoyaltyRedemptionResponse toResponse(String message) {
            return new LoyaltyRedemptionResponse(redemptionId, rewardId, rewardName, status, points, discountAmount, message);
        }
    }

    private record EarnLedger(long accountId, int points, BigDecimal monetaryValue) {
    }

    private record ConfirmedRedemption(long redemptionId,
                                       long accountId,
                                       long rewardId,
                                       String rewardName,
                                       int points,
                                       BigDecimal discountAmount) {
    }
}
