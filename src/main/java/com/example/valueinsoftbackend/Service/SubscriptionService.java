package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbApp.DbSubscription;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SubscriptionService {

    private final DbSubscription dbSubscription;
    private final PayMobService payMobService;

    public SubscriptionService(DbSubscription dbSubscription, PayMobService payMobService) {
        this.dbSubscription = dbSubscription;
        this.payMobService = payMobService;
    }

    public List<AppModelSubscription> getBranchSubscription(int branchId) {
        return dbSubscription.getBranchSubscription(branchId);
    }

    @Transactional
    public String addBranchSubscription(CreateSubscriptionRequest request) {
        validateSubscription(request);
        BigDecimal initialAmountPaid = request.getAmountPaid() == null ? BigDecimal.ZERO : request.getAmountPaid();
        AppModelSubscription subscription = dbSubscription.toSubscription(
                Date.valueOf(request.getStartTime()),
                Date.valueOf(request.getEndTime()),
                request.getBranchId(),
                request.getAmountToPay(),
                initialAmountPaid,
                0,
                "NP"
        );

        int subscriptionId = dbSubscription.createBranchSubscription(subscription);
        int payMobOrderId = payMobService.createPayMobOrder(subscriptionId, request.getBranchId(), request.getAmountToPay());
        dbSubscription.updateBranchSubscriptionWithPayMobId(payMobOrderId, subscriptionId);

        log.info("Created subscription {} for branch {} with PayMob order {}", subscriptionId, request.getBranchId(), payMobOrderId);
        return "the Add BranchSubscription Added Successfully : " + request.getBranchId();
    }

    @Transactional
    public void markBranchSubscriptionStatusSuccess(int orderId) {
        TenantSqlIdentifiers.requirePositive(orderId, "orderId");
        dbSubscription.markBranchSubscriptionPaid(orderId);
        log.info("Marked subscription as paid for PayMob order {}", orderId);
    }

    public Map<String, Object> isActive(int branchId) {
        return dbSubscription.isActive(branchId);
    }

    private void validateSubscription(CreateSubscriptionRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_DATE_RANGE_INVALID", "endTime must be on or after startTime");
        }
        if (request.getAmountPaid() != null && request.getAmountPaid().compareTo(request.getAmountToPay()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_AMOUNT_INVALID", "amountPaid cannot be greater than amountToPay");
        }
    }
}
