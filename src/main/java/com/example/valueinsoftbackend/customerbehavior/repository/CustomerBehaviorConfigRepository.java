package com.example.valueinsoftbackend.customerbehavior.repository;

import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class CustomerBehaviorConfigRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerBehaviorConfigRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CustomerBehaviorConfig> findCompanyConfig(long companyId) {
        try {
            CustomerBehaviorConfig config = jdbcTemplate.queryForObject(
                    """
                            SELECT branch_id,
                                   new_customer_days,
                                   active_customer_days,
                                   at_risk_days,
                                   dormant_days,
                                   loyal_min_orders,
                                   vip_min_orders,
                                   vip_min_spend,
                                   discount_sensitive_ratio,
                                   return_risk_ratio,
                                   minimum_affinity_support,
                                   currency_code,
                                   timezone
                            FROM public.customer_behavior_config
                            WHERE company_id = :companyId
                              AND branch_id IS NULL
                            LIMIT 1
                            """,
                    new MapSqlParameterSource("companyId", companyId),
                    (rs, rowNum) -> new CustomerBehaviorConfig(
                            (Integer) rs.getObject("branch_id"),
                            rs.getInt("new_customer_days"),
                            rs.getInt("active_customer_days"),
                            rs.getInt("at_risk_days"),
                            rs.getInt("dormant_days"),
                            rs.getInt("loyal_min_orders"),
                            rs.getInt("vip_min_orders"),
                            rs.getBigDecimal("vip_min_spend"),
                            rs.getBigDecimal("discount_sensitive_ratio"),
                            rs.getBigDecimal("return_risk_ratio"),
                            rs.getInt("minimum_affinity_support"),
                            rs.getString("currency_code"),
                            rs.getString("timezone")
                    )
            );
            return Optional.ofNullable(config);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public CustomerBehaviorConfig saveCompanyConfig(long companyId, CustomerBehaviorConfig config) {
        CustomerBehaviorConfig safe = sanitize(config);
        jdbcTemplate.update(
                """
                        INSERT INTO public.customer_behavior_config (
                            company_id,
                            branch_id,
                            new_customer_days,
                            active_customer_days,
                            at_risk_days,
                            dormant_days,
                            loyal_min_orders,
                            vip_min_orders,
                            vip_min_spend,
                            discount_sensitive_ratio,
                            return_risk_ratio,
                            minimum_affinity_support,
                            currency_code,
                            timezone
                        ) VALUES (
                            :companyId,
                            NULL,
                            :newCustomerDays,
                            :activeCustomerDays,
                            :atRiskDays,
                            :dormantDays,
                            :loyalMinOrders,
                            :vipMinOrders,
                            :vipMinSpend,
                            :discountSensitiveRatio,
                            :returnRiskRatio,
                            :minimumAffinitySupport,
                            :currencyCode,
                            :timezone
                        )
                        ON CONFLICT (company_id) WHERE branch_id IS NULL DO UPDATE
                        SET new_customer_days = EXCLUDED.new_customer_days,
                            active_customer_days = EXCLUDED.active_customer_days,
                            at_risk_days = EXCLUDED.at_risk_days,
                            dormant_days = EXCLUDED.dormant_days,
                            loyal_min_orders = EXCLUDED.loyal_min_orders,
                            vip_min_orders = EXCLUDED.vip_min_orders,
                            vip_min_spend = EXCLUDED.vip_min_spend,
                            discount_sensitive_ratio = EXCLUDED.discount_sensitive_ratio,
                            return_risk_ratio = EXCLUDED.return_risk_ratio,
                            minimum_affinity_support = EXCLUDED.minimum_affinity_support,
                            currency_code = EXCLUDED.currency_code,
                            timezone = EXCLUDED.timezone,
                            updated_at = now()
                        """,
                params(companyId, safe)
        );
        return safe;
    }

    private CustomerBehaviorConfig sanitize(CustomerBehaviorConfig config) {
        CustomerBehaviorConfig defaults = CustomerBehaviorConfig.defaults(
                config == null ? null : config.currencyCode(),
                config == null ? null : config.timezone()
        );
        if (config == null) {
            return defaults;
        }
        int newCustomerDays = positive(config.newCustomerDays(), defaults.newCustomerDays());
        int activeCustomerDays = positive(config.activeCustomerDays(), defaults.activeCustomerDays());
        int atRiskDays = positive(config.atRiskDays(), defaults.atRiskDays());
        int dormantDays = Math.max(positive(config.dormantDays(), defaults.dormantDays()), atRiskDays + 1);
        int loyalMinOrders = positive(config.loyalMinOrders(), defaults.loyalMinOrders());
        int vipMinOrders = Math.max(positive(config.vipMinOrders(), defaults.vipMinOrders()), loyalMinOrders);
        int minimumAffinitySupport = positive(config.minimumAffinitySupport(), defaults.minimumAffinitySupport());
        return new CustomerBehaviorConfig(
                null,
                newCustomerDays,
                activeCustomerDays,
                atRiskDays,
                dormantDays,
                loyalMinOrders,
                vipMinOrders,
                nonNegative(config.vipMinSpend(), defaults.vipMinSpend()),
                ratio(config.discountSensitiveRatio(), defaults.discountSensitiveRatio()),
                ratio(config.returnRiskRatio(), defaults.returnRiskRatio()),
                minimumAffinitySupport,
                blankToDefault(config.currencyCode(), defaults.currencyCode()),
                blankToDefault(config.timezone(), defaults.timezone())
        );
    }

    private MapSqlParameterSource params(long companyId, CustomerBehaviorConfig config) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("newCustomerDays", config.newCustomerDays())
                .addValue("activeCustomerDays", config.activeCustomerDays())
                .addValue("atRiskDays", config.atRiskDays())
                .addValue("dormantDays", config.dormantDays())
                .addValue("loyalMinOrders", config.loyalMinOrders())
                .addValue("vipMinOrders", config.vipMinOrders())
                .addValue("vipMinSpend", config.vipMinSpend())
                .addValue("discountSensitiveRatio", config.discountSensitiveRatio())
                .addValue("returnRiskRatio", config.returnRiskRatio())
                .addValue("minimumAffinitySupport", config.minimumAffinitySupport())
                .addValue("currencyCode", config.currencyCode())
                .addValue("timezone", config.timezone());
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return fallback;
        }
        return value;
    }

    private BigDecimal ratio(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            return fallback;
        }
        return value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
