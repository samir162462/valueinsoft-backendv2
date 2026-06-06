package com.example.valueinsoftbackend.customerbehavior.service;

import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorConfig;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerBehaviorSegmentationService {

    public SegmentDecision classify(CustomerBehaviorClassificationInput input, CustomerBehaviorConfig config) {
        List<CustomerSegment> flags = new ArrayList<>();

        if (input.historicalOrders() <= 0) {
            if (input.daysSinceRegistration() != null && input.daysSinceRegistration() <= config.newCustomerDays()) {
                flags.add(CustomerSegment.NEW);
            } else {
                flags.add(CustomerSegment.DORMANT);
            }
            CustomerSegment primary = primary(flags);
            List<CustomerSegment> secondary = new ArrayList<>(flags);
            secondary.remove(primary);
            return new SegmentDecision(primary, secondary);
        }

        if (input.returnRatio().compareTo(config.returnRiskRatio()) >= 0 && input.historicalOrders() > 0) {
            flags.add(CustomerSegment.RETURN_RISK);
        }
        if (input.daysSinceLastPurchase() != null && input.daysSinceLastPurchase() >= config.dormantDays()) {
            flags.add(CustomerSegment.DORMANT);
        }
        if (input.daysSinceLastPurchase() != null
                && input.daysSinceLastPurchase() >= config.atRiskDays()
                && input.daysSinceLastPurchase() < config.dormantDays()) {
            flags.add(CustomerSegment.AT_RISK);
        }
        if (input.orders() >= config.vipMinOrders()
                && input.netSpend().compareTo(config.vipMinSpend()) >= 0) {
            flags.add(CustomerSegment.VIP);
        }
        if (input.orders() >= config.loyalMinOrders()) {
            flags.add(CustomerSegment.LOYAL);
        }
        if (input.daysSinceRegistration() != null && input.daysSinceRegistration() <= config.newCustomerDays()) {
            flags.add(CustomerSegment.NEW);
        }
        if (input.discountRatio().compareTo(config.discountSensitiveRatio()) >= 0 && input.orders() > 0) {
            flags.add(CustomerSegment.DISCOUNT_SENSITIVE);
        }
        if (input.categoryConcentration().compareTo(BigDecimal.valueOf(0.65)) >= 0 && input.orders() > 0) {
            flags.add(CustomerSegment.CATEGORY_LOYAL);
        }
        if (input.daysSinceLastPurchase() != null && input.daysSinceLastPurchase() <= config.activeCustomerDays()) {
            flags.add(CustomerSegment.ACTIVE);
        }

        CustomerSegment primary = primary(flags);
        List<CustomerSegment> secondary = new ArrayList<>(flags);
        secondary.remove(primary);
        return new SegmentDecision(primary, secondary);
    }

    private CustomerSegment primary(List<CustomerSegment> flags) {
        for (CustomerSegment segment : List.of(
                CustomerSegment.RETURN_RISK,
                CustomerSegment.DORMANT,
                CustomerSegment.AT_RISK,
                CustomerSegment.VIP,
                CustomerSegment.LOYAL,
                CustomerSegment.NEW,
                CustomerSegment.DISCOUNT_SENSITIVE,
                CustomerSegment.CATEGORY_LOYAL,
                CustomerSegment.ACTIVE
        )) {
            if (flags.contains(segment)) {
                return segment;
            }
        }
        return CustomerSegment.ACTIVE;
    }

    public record CustomerBehaviorClassificationInput(
            long orders,
            long historicalOrders,
            BigDecimal netSpend,
            BigDecimal discountRatio,
            BigDecimal returnRatio,
            BigDecimal categoryConcentration,
            Long daysSinceLastPurchase,
            Long daysSinceRegistration
    ) {
    }

    public record SegmentDecision(CustomerSegment primary, List<CustomerSegment> secondaryFlags) {
    }
}
