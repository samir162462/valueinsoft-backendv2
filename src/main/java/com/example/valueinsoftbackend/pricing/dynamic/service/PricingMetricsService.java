package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsItemResponse;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PricingMetricsResponse;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class PricingMetricsService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_WINDOW_DAYS = 180;

    private final PricingMetricsRepository repository;
    private final DynamicPricingSecurityService securityService;

    public PricingMetricsService(PricingMetricsRepository repository,
                                 DynamicPricingSecurityService securityService) {
        this.repository = repository;
        this.securityService = securityService;
    }

    public PricingMetricsResponse previewMetrics(String actorName, PricingMetricsRequest request) {
        securityService.requireView(actorName, request.companyId(), request.branchId());
        validateDateWindow(request.fromDate(), request.toDate());
        boolean includeCostDetails = securityService.canReadCost(actorName, request.companyId(), request.branchId());

        PricingMetricsRepository.MetricsPage page = repository.findMetrics(new PricingMetricsRepository.MetricsQuery(
                request.companyId(),
                request.branchId(),
                request.fromDate(),
                request.toDate(),
                request.query(),
                request.productIds(),
                request.category(),
                request.major(),
                request.businessLineKey(),
                request.templateKey(),
                request.supplierId(),
                request.page() == null ? DEFAULT_PAGE : request.page(),
                request.size() == null ? DEFAULT_SIZE : request.size()
        ));

        List<PricingMetricsItemResponse> items = page.items().stream()
                .map(snapshot -> PricingMetricsItemResponse.from(snapshot, includeCostDetails))
                .toList();

        return new PricingMetricsResponse(
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                includeCostDetails,
                items
        );
    }

    private void validateDateWindow(LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_METRICS_DATE_RANGE_INVALID",
                    "toDate must be greater than or equal to fromDate"
            );
        }
        long days = ChronoUnit.DAYS.between(fromDate, toDate.plusDays(1));
        if (days > MAX_WINDOW_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRICING_METRICS_DATE_RANGE_TOO_LARGE",
                    "Pricing metrics date range cannot exceed " + MAX_WINDOW_DAYS + " days"
            );
        }
    }
}
