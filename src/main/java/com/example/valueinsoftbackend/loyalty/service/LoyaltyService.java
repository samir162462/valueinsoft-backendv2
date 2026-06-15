package com.example.valueinsoftbackend.loyalty.service;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyAccountResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyEstimateRequest;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyEstimateResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyLedgerItem;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyProgramConfig;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRecordedEarn;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRedemptionRequest;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRedemptionResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyReversalResult;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRewardResponse;
import com.example.valueinsoftbackend.loyalty.repository.LoyaltyRepository;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LoyaltyService {

    private final LoyaltyRepository loyaltyRepository;

    public LoyaltyService(LoyaltyRepository loyaltyRepository) {
        this.loyaltyRepository = loyaltyRepository;
    }

    public LoyaltyAccountResponse getOrCreateAccount(int companyId, int branchId, int clientId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        return loyaltyRepository.getOrCreateAccount(companyId, branchId, clientId);
    }

    public LoyaltyEstimateResponse estimate(int companyId, LoyaltyEstimateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");

        LoyaltyProgramConfig config = loyaltyRepository.getEffectiveConfig(companyId, request.branchId());
        BigDecimal netAmount = money(request.netAmount());
        int currentPoints = 0;
        if (request.clientId() > 0) {
            LoyaltyAccountResponse account = loyaltyRepository.getOrCreateAccount(companyId, request.branchId(), request.clientId());
            currentPoints = account == null ? 0 : account.availablePoints();
        }

        if (request.clientId() <= 0) {
            return new LoyaltyEstimateResponse(false, 0, 0, config.pointsName(), config.earnPoints(), config.earnAmount(),
                    "Select a customer to earn loyalty points.");
        }
        if (!config.active()) {
            return new LoyaltyEstimateResponse(false, 0, currentPoints, config.pointsName(), config.earnPoints(), config.earnAmount(),
                    "Loyalty points are paused for this branch.");
        }
        if (netAmount.compareTo(config.minEligibleAmount()) < 0) {
            return new LoyaltyEstimateResponse(false, 0, currentPoints, config.pointsName(), config.earnPoints(), config.earnAmount(),
                    "Order total is below the loyalty minimum.");
        }

        int points = calculateEarnPoints(config, netAmount);
        return new LoyaltyEstimateResponse(points > 0, points, currentPoints, config.pointsName(), config.earnPoints(), config.earnAmount(),
                points > 0 ? "Customer will earn " + points + " " + config.pointsName() + "." : "No points earned for this order.");
    }

    public List<LoyaltyLedgerItem> ledgerForClient(int companyId, int branchId, int clientId, int limit) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        return loyaltyRepository.listLedgerByClient(companyId, clientId, limit);
    }

    public List<LoyaltyRewardResponse> eligibleRewards(int companyId, LoyaltyRedemptionRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.clientId(), "clientId");
        return loyaltyRepository.listRewards(companyId, request.branchId(), request.clientId(), money(request.orderNetAmount()));
    }

    @Transactional
    public LoyaltyRedemptionResponse reserveRedemption(int companyId, LoyaltyRedemptionRequest request, String actor) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(request.branchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.clientId(), "clientId");
        try {
            return loyaltyRepository.reserveRedemption(
                    companyId,
                    request.branchId(),
                    request.clientId(),
                    request.rewardId(),
                    money(request.orderNetAmount()),
                    actor);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "LOYALTY_REDEMPTION_NOT_AVAILABLE", exception.getMessage());
        }
    }

    @Transactional
    public LoyaltyRedemptionResponse releaseRedemption(int companyId, long redemptionId, String actor) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        try {
            return loyaltyRepository.releaseRedemption(companyId, redemptionId, actor);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LOYALTY_REDEMPTION_NOT_FOUND", exception.getMessage());
        }
    }

    @Transactional
    public LoyaltyRedemptionResponse confirmOrderRedemption(int companyId, Order order, DbPosOrder.AddOrderResult result) {
        if (order == null || result == null || order.getLoyaltyRedemptionId() == null || order.getLoyaltyRedemptionId() <= 0) {
            return null;
        }
        try {
            return loyaltyRepository.confirmRedemption(
                    companyId,
                    order.getBranchId(),
                    order.getLoyaltyRedemptionId(),
                    result.orderId(),
                    order.getSalesUser());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "LOYALTY_REDEMPTION_CONFIRM_FAILED", exception.getMessage());
        }
    }

    @Transactional
    public LoyaltyReversalResult reverseOrderDetailReturn(int companyId,
                                                          int branchId,
                                                          DbPosOrder.OrderBounceBackContext context,
                                                          int refundAmount,
                                                          boolean fullReturn) {
        if (context == null || context.getClientId() == null || context.getClientId() <= 0) {
            return new LoyaltyReversalResult(0, 0, false);
        }
        return loyaltyRepository.reverseForReturn(
                companyId,
                branchId,
                context.getClientId(),
                context.getOrderId(),
                context.getOrderDetailId(),
                BigDecimal.valueOf(Math.max(0, refundAmount)),
                BigDecimal.valueOf(Math.max(0, context.getOrderTotal())),
                fullReturn,
                context.getSalesUser());
    }

    @Transactional
    public LoyaltyRecordedEarn recordOrderEarn(int companyId, Order order, DbPosOrder.AddOrderResult result) {
        if (order == null || result == null || order.getClientId() <= 0 || order.getOrderTotal() <= 0) {
            return new LoyaltyRecordedEarn(0, order == null ? 0 : order.getClientId(), 0, false);
        }

        LoyaltyProgramConfig config = loyaltyRepository.getEffectiveConfig(companyId, order.getBranchId());
        BigDecimal netAmount = BigDecimal.valueOf(order.getOrderTotal());
        if (!config.active() || netAmount.compareTo(config.minEligibleAmount()) < 0) {
            return new LoyaltyRecordedEarn(0, order.getClientId(), 0, false);
        }

        int points = calculateEarnPoints(config, netAmount);
        if (points <= 0) {
            return new LoyaltyRecordedEarn(0, order.getClientId(), 0, false);
        }

        return loyaltyRepository.recordOrderEarn(
                companyId,
                order.getBranchId(),
                order.getClientId(),
                result.orderId(),
                points,
                netAmount,
                order.getSalesUser(),
                config.expiryMonths());
    }

    public LoyaltyProgramConfig getEffectiveConfig(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return loyaltyRepository.getEffectiveConfig(companyId, branchId);
    }

    @Transactional
    public void updateConfig(int companyId, LoyaltyProgramConfig config) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        loyaltyRepository.updateConfig(companyId, config);
    }

    public List<LoyaltyRewardResponse> listAllRewards(int companyId, Integer branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return loyaltyRepository.listAllRewards(companyId, branchId);
    }

    @Transactional
    public void createReward(int companyId, LoyaltyRewardResponse reward, Integer branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        loyaltyRepository.createReward(companyId, reward, branchId);
    }

    @Transactional
    public void updateReward(int companyId, LoyaltyRewardResponse reward) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        loyaltyRepository.updateReward(companyId, reward);
    }

    @Transactional
    public void deleteReward(int companyId, long rewardId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        loyaltyRepository.deleteReward(companyId, rewardId);
    }


    private int calculateEarnPoints(LoyaltyProgramConfig config, BigDecimal netAmount) {
        if (config == null || netAmount == null || netAmount.signum() <= 0 || config.earnAmount().signum() <= 0) {
            return 0;
        }
        BigDecimal units = netAmount.divideToIntegralValue(config.earnAmount());
        return units.multiply(BigDecimal.valueOf(config.earnPoints())).intValue();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
