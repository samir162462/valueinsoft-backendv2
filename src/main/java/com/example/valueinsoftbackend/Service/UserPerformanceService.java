package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Config.CacheConfig;
import com.example.valueinsoftbackend.DatabaseRequests.DbUserPerformance;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Response.UserPerformanceResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class UserPerformanceService {

    private static final List<String> VALID_PERIODS = List.of("TODAY", "WEEK", "MONTH", "QUARTER", "YEAR");

    private final DbUserPerformance dbUserPerformance;

    public UserPerformanceService(DbUserPerformance dbUserPerformance) {
        this.dbUserPerformance = dbUserPerformance;
    }

    @Cacheable(
            cacheNames = CacheConfig.USER_PERFORMANCE,
            key = "#companyId + ':' + #branchId + ':' + #salesUser + ':' + #period"
    )
    public UserPerformanceResponse getPerformance(int companyId, int branchId, String salesUser, String period) {
        String normalizedPeriod = normalizePeriod(period);
        LocalDate today = LocalDate.now();

        PeriodRange range = resolveRange(normalizedPeriod, today);
        Duration periodDuration = Duration.between(range.from(), range.to());
        LocalDateTime previousFrom = range.from().minus(periodDuration);
        LocalDateTime previousTo = range.from();

        UserPerformanceResponse.PerformanceTotals current = dbUserPerformance.getTotals(
                companyId, branchId, salesUser, range.from(), range.to());
        UserPerformanceResponse.PerformanceTotals previous = dbUserPerformance.getTotals(
                companyId, branchId, salesUser, previousFrom, previousTo);
        List<UserPerformanceResponse.PerformancePoint> series = dbUserPerformance.getSeries(
                companyId, branchId, salesUser, range.from(), range.to(), range.bucketUnit());

        UserPerformanceResponse response = new UserPerformanceResponse();
        response.setUserName(salesUser);
        response.setPeriod(normalizedPeriod);
        response.setPeriodLabel(periodLabel(normalizedPeriod));
        response.setStartDate(range.from().toLocalDate().toString());
        response.setEndDate(range.to().toLocalDate().minusDays(1).toString());
        response.setCurrency("EGP");
        response.setGeneratedAt(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        response.setCurrent(current);
        response.setPrevious(previous);
        response.setSalesChangePct(percentChange(previous.getSalesTotal(), current.getSalesTotal()));
        response.setOrdersChangePct(percentChange(previous.getOrdersCount(), current.getOrdersCount()));
        response.setSeries(series);

        return response;
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "TODAY";
        }
        String upper = period.trim().toUpperCase(Locale.ROOT);
        if (!VALID_PERIODS.contains(upper)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "period must be one of " + VALID_PERIODS);
        }
        return upper;
    }

    private PeriodRange resolveRange(String period, LocalDate today) {
        LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

        return switch (period) {
            case "WEEK" -> new PeriodRange(today.minusDays(6).atStartOfDay(), endExclusive, "day");
            case "MONTH" -> new PeriodRange(today.minusDays(29).atStartOfDay(), endExclusive, "day");
            case "QUARTER" -> new PeriodRange(today.minusMonths(3).atStartOfDay(), endExclusive, "week");
            case "YEAR" -> new PeriodRange(today.minusYears(1).plusDays(1).atStartOfDay(), endExclusive, "month");
            default -> new PeriodRange(today.atStartOfDay(), endExclusive, "hour");
        };
    }

    private String periodLabel(String period) {
        return switch (period) {
            case "WEEK" -> "Last 7 days";
            case "MONTH" -> "Last 30 days";
            case "QUARTER" -> "Last 3 months";
            case "YEAR" -> "Last 12 months";
            default -> "Today";
        };
    }

    private double percentChange(double previousValue, double currentValue) {
        if (previousValue == 0) {
            return currentValue > 0 ? 100.0 : 0.0;
        }
        return Math.round(((currentValue - previousValue) / previousValue) * 1000.0) / 10.0;
    }

    private record PeriodRange(LocalDateTime from, LocalDateTime to, String bucketUnit) {
    }
}
