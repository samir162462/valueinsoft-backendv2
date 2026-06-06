package com.example.valueinsoftbackend.customerbehavior.service;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.customerbehavior.config.CustomerBehaviorMetricRules;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorConfig;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorFilter;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorOverview;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorPage;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorProfile;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorRow;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceSummary;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerProductAffinity;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRecentOrder;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRetentionCohort;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegment;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegmentSummary;
import com.example.valueinsoftbackend.customerbehavior.repository.CustomerBehaviorConfigRepository;
import com.example.valueinsoftbackend.customerbehavior.repository.CustomerBehaviorMetricRecord;
import com.example.valueinsoftbackend.customerbehavior.repository.CustomerBehaviorQueryScope;
import com.example.valueinsoftbackend.customerbehavior.repository.CustomerBehaviorRepository;
import com.example.valueinsoftbackend.customerbehavior.security.CustomerBehaviorRequestContext;
import com.example.valueinsoftbackend.customerbehavior.security.CustomerBehaviorSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CustomerBehaviorService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int MAX_LIVE_RANGE_DAYS = 366;

    private final CustomerBehaviorRepository repository;
    private final CustomerBehaviorConfigRepository configRepository;
    private final CustomerBehaviorSegmentationService segmentationService;
    private final CustomerBehaviorSecurityService securityService;
    private final CustomerBehaviorAuditService auditService;
    private final DbCompany dbCompany;

    public CustomerBehaviorService(CustomerBehaviorRepository repository,
                                   CustomerBehaviorConfigRepository configRepository,
                                   CustomerBehaviorSegmentationService segmentationService,
                                   CustomerBehaviorSecurityService securityService,
                                   CustomerBehaviorAuditService auditService,
                                   DbCompany dbCompany) {
        this.repository = repository;
        this.configRepository = configRepository;
        this.segmentationService = segmentationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.dbCompany = dbCompany;
    }

    public CustomerBehaviorOverview getOverview(CustomerBehaviorFilter filter, Principal principal) {
        return getOverview(securityService.authorizeView(principal, filter), filter);
    }

    public CustomerBehaviorOverview getOverview(AiSecurityContext context, CustomerBehaviorFilter filter) {
        return getOverview(securityService.authorizeView(context, filter), filter);
    }

    public List<CustomerSegmentSummary> getSegments(CustomerBehaviorFilter filter, Principal principal) {
        return getOverview(filter, principal).segments();
    }

    public CustomerBehaviorPage<CustomerBehaviorRow> searchCustomers(CustomerBehaviorFilter filter, Principal principal) {
        return searchCustomers(securityService.authorizeView(principal, filter), filter);
    }

    public CustomerBehaviorPage<CustomerBehaviorRow> searchCustomers(AiSecurityContext context, CustomerBehaviorFilter filter) {
        return searchCustomers(securityService.authorizeView(context, filter), filter);
    }

    public CustomerPreferenceSummary getPreferences(CustomerBehaviorFilter filter, Principal principal) {
        return getPreferences(securityService.authorizeView(principal, filter), filter, 10);
    }

    public CustomerPreferenceSummary getPreferences(AiSecurityContext context, CustomerBehaviorFilter filter, int limit) {
        return getPreferences(securityService.authorizeView(context, filter), filter, limit);
    }

    public List<CustomerProductAffinity> getAffinity(CustomerBehaviorFilter filter, Principal principal) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(principal, filter);
        return getAffinity(requestContext, filter);
    }

    public List<CustomerProductAffinity> getAffinity(AiSecurityContext context, CustomerBehaviorFilter filter) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(context, filter);
        return getAffinity(requestContext, filter);
    }

    private List<CustomerProductAffinity> getAffinity(CustomerBehaviorRequestContext requestContext, CustomerBehaviorFilter filter) {
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        return repository.findProductAffinity(scope, config.minimumAffinitySupport(), 50);
    }

    public List<CustomerRetentionCohort> getCohorts(CustomerBehaviorFilter filter, Principal principal) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(principal, filter);
        return getCohorts(requestContext, filter);
    }

    public List<CustomerRetentionCohort> getCohorts(AiSecurityContext context, CustomerBehaviorFilter filter) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(context, filter);
        return getCohorts(requestContext, filter);
    }

    private List<CustomerRetentionCohort> getCohorts(CustomerBehaviorRequestContext requestContext, CustomerBehaviorFilter filter) {
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        return repository.findRetentionCohorts(scope);
    }

    public CustomerBehaviorProfile getCustomerProfile(long customerId,
                                                      List<Integer> branchIds,
                                                      LocalDate fromDate,
                                                      LocalDate toDate,
                                                      String locale,
                                                      Principal principal) {
        CustomerBehaviorFilter filter = new CustomerBehaviorFilter(branchIds, fromDate, toDate, null, null, null, 0, 1, null, null);
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(principal, filter);
        return getCustomerProfile(requestContext, customerId, filter, locale);
    }

    public CustomerBehaviorProfile getCustomerProfile(AiSecurityContext context,
                                                      long customerId,
                                                      CustomerBehaviorFilter filter) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeView(context, filter);
        return getCustomerProfile(requestContext, customerId, filter, null);
    }

    private CustomerBehaviorProfile getCustomerProfile(CustomerBehaviorRequestContext requestContext,
                                                       long customerId,
                                                       CustomerBehaviorFilter filter,
                                                       String locale) {
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        List<CustomerBehaviorRow> rows = materializeRows(scope, config);
        CustomerBehaviorRow row = rows.stream()
                .filter(item -> item.customerId() == customerId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_BEHAVIOR_CUSTOMER_NOT_FOUND", "Customer was not found in the selected branch scope"));
        List<CustomerRecentOrder> recentOrders = repository.findRecentOrders(scope, customerId, 10);
        return new CustomerBehaviorProfile(
                row,
                recentOrders,
                repository.findCustomerTopProducts(scope, customerId, 10),
                repository.findCustomerTopCategories(scope, customerId, 10),
                repository.findSpendTrend(scope, customerId),
                deterministicCustomerRecommendation(row, locale),
                CustomerBehaviorMetricRules.defaultDataQualityWarnings()
        );
    }

    public CustomerBehaviorConfig getConfig(Principal principal) {
        AiSecurityContext context = securityService.authorizeConfigRead(principal);
        return loadConfig(context.companyId());
    }

    public CustomerBehaviorConfig updateConfig(CustomerBehaviorConfig config, Principal principal) {
        long startedAt = System.nanoTime();
        AiSecurityContext context = securityService.authorizeConfigWrite(principal);
        CustomerBehaviorConfig saved = configRepository.saveCompanyConfig(context.companyId(), config);
        auditService.log(context.companyId(), null, context.userId(), "CONFIG_UPDATED", saved, true, elapsedMs(startedAt));
        return saved;
    }

    CustomerBehaviorQueryScope resolveScopeForAi(AiSecurityContext context, CustomerBehaviorFilter filter) {
        CustomerBehaviorRequestContext requestContext = securityService.authorizeAi(context, filter);
        return buildScope(requestContext, filter, loadConfig(context.companyId()));
    }

    CustomerBehaviorConfig loadConfig(long companyId) {
        Company company = dbCompany.getCompanyById(Math.toIntExact(companyId));
        String companyCurrency = company == null ? null : company.getCurrency();
        CustomerBehaviorConfig defaults = CustomerBehaviorConfig.defaults(companyCurrency, "Africa/Cairo");
        return configRepository.findCompanyConfig(companyId).orElse(defaults);
    }

    private CustomerBehaviorOverview getOverview(CustomerBehaviorRequestContext requestContext, CustomerBehaviorFilter filter) {
        long startedAt = System.nanoTime();
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        List<CustomerBehaviorRow> rows = materializeRows(scope, config);
        CustomerBehaviorOverview overview = buildOverview(scope, config, rows);
        log.info("Customer behavior overview companyId={} branches={} rows={} durationMs={}",
                requestContext.companyId(), requestContext.branchIds(), rows.size(), elapsedMs(startedAt));
        return overview;
    }

    private CustomerBehaviorPage<CustomerBehaviorRow> searchCustomers(CustomerBehaviorRequestContext requestContext, CustomerBehaviorFilter filter) {
        long startedAt = System.nanoTime();
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        List<CustomerBehaviorRow> rows = materializeRows(scope, config);
        List<CustomerBehaviorRow> filtered = rows.stream()
                .filter(row -> matchesFilter(row, filter))
                .sorted(comparator(filter))
                .toList();
        CustomerBehaviorPage<CustomerBehaviorRow> page = page(filtered, filter);
        log.info("Customer behavior customer search companyId={} branches={} rows={} returned={} durationMs={}",
                requestContext.companyId(), requestContext.branchIds(), filtered.size(), page.items().size(), elapsedMs(startedAt));
        return page;
    }

    private CustomerPreferenceSummary getPreferences(CustomerBehaviorRequestContext requestContext,
                                                     CustomerBehaviorFilter filter,
                                                     int limit) {
        CustomerBehaviorConfig config = loadConfig(requestContext.companyId());
        CustomerBehaviorQueryScope scope = buildScope(requestContext, filter, config);
        return new CustomerPreferenceSummary(
                repository.findTopProducts(scope, limit),
                repository.findTopCategories(scope, limit),
                CustomerBehaviorMetricRules.defaultDataQualityWarnings()
        );
    }

    private CustomerBehaviorOverview buildOverview(CustomerBehaviorQueryScope scope,
                                                   CustomerBehaviorConfig config,
                                                   List<CustomerBehaviorRow> rows) {
        long registered = rows.stream()
                .filter(row -> row.branchId() != null && scope.branchIds().contains(row.branchId()))
                .count();
        long purchasing = rows.stream().filter(row -> row.orders() > 0).count();
        long repeat = rows.stream().filter(row -> row.orders() >= 2).count();
        long active = rows.stream().filter(row -> row.daysSinceLastPurchase() != null
                && row.daysSinceLastPurchase() <= config.activeCustomerDays()).count();
        long atRisk = rows.stream().filter(row -> row.segment() == CustomerSegment.AT_RISK
                || row.secondaryFlags().contains(CustomerSegment.AT_RISK)).count();

        BigDecimal gross = rows.stream().map(CustomerBehaviorRow::grossSpend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = rows.stream().map(CustomerBehaviorRow::totalSpend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = rows.stream().map(CustomerBehaviorRow::discountTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = rows.stream().map(CustomerBehaviorRow::returnTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long orders = rows.stream().mapToLong(CustomerBehaviorRow::orders).sum();
        BigDecimal basketNumerator = rows.stream()
                .map(row -> row.averageBasketSize().multiply(BigDecimal.valueOf(row.orders())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CustomerBehaviorOverview(
                scope.fromDate(),
                scope.toDate(),
                scope.branchIds(),
                config.currencyCode(),
                config.timezone(),
                registered,
                purchasing,
                active,
                repeat,
                CustomerBehaviorMath.ratio(repeat, purchasing),
                CustomerBehaviorMath.divide(net, orders),
                net,
                gross,
                discount.setScale(4, RoundingMode.HALF_UP),
                returned.setScale(4, RoundingMode.HALF_UP),
                CustomerBehaviorMath.divide(basketNumerator, orders),
                atRisk,
                segmentSummaries(rows),
                CustomerBehaviorMetricRules.defaultDataQualityWarnings()
        );
    }

    private List<CustomerSegmentSummary> segmentSummaries(List<CustomerBehaviorRow> rows) {
        Map<CustomerSegment, List<CustomerBehaviorRow>> bySegment = new EnumMap<>(CustomerSegment.class);
        for (CustomerSegment segment : CustomerSegment.values()) {
            bySegment.put(segment, new ArrayList<>());
        }
        for (CustomerBehaviorRow row : rows) {
            bySegment.get(row.segment()).add(row);
        }

        List<CustomerSegmentSummary> summaries = new ArrayList<>();
        for (CustomerSegment segment : CustomerSegment.values()) {
            List<CustomerBehaviorRow> segmentRows = bySegment.get(segment);
            long customers = segmentRows.size();
            long purchasingCustomers = segmentRows.stream().filter(row -> row.orders() > 0).count();
            long repeatCustomers = segmentRows.stream().filter(row -> row.orders() >= 2).count();
            long orders = segmentRows.stream().mapToLong(CustomerBehaviorRow::orders).sum();
            BigDecimal netSpend = segmentRows.stream().map(CustomerBehaviorRow::totalSpend).reduce(BigDecimal.ZERO, BigDecimal::add);
            summaries.add(new CustomerSegmentSummary(
                    segment,
                    label(segment),
                    customers,
                    netSpend,
                    CustomerBehaviorMath.divide(netSpend, orders),
                    CustomerBehaviorMath.ratio(repeatCustomers, purchasingCustomers)
            ));
        }
        return summaries;
    }

    private List<CustomerBehaviorRow> materializeRows(CustomerBehaviorQueryScope scope, CustomerBehaviorConfig config) {
        return repository.findCustomerMetrics(scope).stream()
                .map(record -> toRow(record, scope.toDate(), config))
                .toList();
    }

    private CustomerBehaviorRow toRow(CustomerBehaviorMetricRecord record,
                                      LocalDate toDate,
                                      CustomerBehaviorConfig config) {
        BigDecimal grossSpend = CustomerBehaviorMath.zeroIfNull(record.grossSpend());
        BigDecimal discountTotal = CustomerBehaviorMath.zeroIfNull(record.discountTotal());
        BigDecimal returnTotal = CustomerBehaviorMath.zeroIfNull(record.returnTotal());
        BigDecimal netSpend = CustomerBehaviorMath.zeroIfNull(record.netSpend());
        BigDecimal purchasedQuantity = CustomerBehaviorMath.zeroIfNull(record.purchasedQuantity());
        BigDecimal discountRatio = CustomerBehaviorMath.divide(discountTotal, grossSpend);
        BigDecimal returnRatio = CustomerBehaviorMath.divide(returnTotal, grossSpend);
        BigDecimal averageOrderValue = CustomerBehaviorMath.divide(netSpend, record.periodOrderCount());
        BigDecimal averageBasketSize = CustomerBehaviorMath.divide(purchasedQuantity, record.periodOrderCount());
        Long daysSinceLastPurchase = record.lastPurchaseAt() == null
                ? null
                : ChronoUnit.DAYS.between(record.lastPurchaseAt().toLocalDate(), toDate);
        Long daysSinceRegistration = record.registeredAt() == null
                ? null
                : ChronoUnit.DAYS.between(record.registeredAt().toLocalDate(), toDate);
        BigDecimal cadenceDays = averageCadence(record.firstPurchaseAt(), record.lastPurchaseAt(), record.historicalOrderCount());
        BigDecimal categoryConcentration = CustomerBehaviorMath.divide(
                CustomerBehaviorMath.zeroIfNull(record.favoriteCategorySpend()),
                CustomerBehaviorMath.zeroIfNull(record.totalCategorySpend())
        );
        CustomerBehaviorSegmentationService.SegmentDecision segment = segmentationService.classify(
                new CustomerBehaviorSegmentationService.CustomerBehaviorClassificationInput(
                        record.periodOrderCount(),
                        record.historicalOrderCount(),
                        netSpend,
                        discountRatio,
                        returnRatio,
                        categoryConcentration,
                        daysSinceLastPurchase,
                        daysSinceRegistration
                ),
                config
        );
        return new CustomerBehaviorRow(
                record.customerId(),
                blankToDefault(record.customerName(), "Unknown customer"),
                maskPhone(record.customerPhone()),
                record.branchId(),
                blankToDefault(record.branchName(), "Unknown branch"),
                segment.primary(),
                segment.secondaryFlags(),
                record.registeredAt(),
                record.lastPurchaseAt(),
                daysSinceLastPurchase,
                record.periodOrderCount(),
                record.historicalOrderCount(),
                netSpend,
                grossSpend,
                discountTotal,
                returnTotal,
                averageOrderValue,
                netSpend,
                cadenceDays,
                averageBasketSize,
                blankToDefault(record.favoriteProduct(), ""),
                blankToDefault(record.favoriteCategory(), ""),
                discountRatio,
                returnRatio
        );
    }

    private CustomerBehaviorQueryScope buildScope(CustomerBehaviorRequestContext requestContext,
                                                  CustomerBehaviorFilter filter,
                                                  CustomerBehaviorConfig config) {
        ZoneId zoneId = zoneId(config.timezone());
        LocalDate today = LocalDate.now(zoneId);
        LocalDate toDate = filter != null && filter.toDate() != null ? filter.toDate() : today;
        LocalDate fromDate = filter != null && filter.fromDate() != null ? filter.fromDate() : toDate.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_BEHAVIOR_INVALID_DATE_RANGE", "fromDate must be on or before toDate");
        }
        long rangeDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (rangeDays > MAX_LIVE_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_BEHAVIOR_DATE_RANGE_TOO_LARGE", "Live analytics supports a maximum date range of 366 days");
        }
        return new CustomerBehaviorQueryScope(
                requestContext.companyId(),
                requestContext.branchIds(),
                fromDate,
                toDate,
                Timestamp.valueOf(fromDate.atStartOfDay()),
                Timestamp.valueOf(toDate.plusDays(1).atStartOfDay())
        );
    }

    private CustomerBehaviorPage<CustomerBehaviorRow> page(List<CustomerBehaviorRow> rows, CustomerBehaviorFilter filter) {
        int page = filter == null || filter.page() == null ? DEFAULT_PAGE : Math.max(0, filter.page());
        int pageSize = filter == null || filter.pageSize() == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(1, filter.pageSize()), MAX_PAGE_SIZE);
        int from = Math.min(page * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        int totalPages = rows.isEmpty() ? 0 : (int) Math.ceil(rows.size() / (double) pageSize);
        return new CustomerBehaviorPage<>(rows.subList(from, to), rows.size(), page, pageSize, totalPages);
    }

    private boolean matchesFilter(CustomerBehaviorRow row, CustomerBehaviorFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.segment() != null && row.segment() != filter.segment() && !row.secondaryFlags().contains(filter.segment())) {
            return false;
        }
        if (filter.minOrders() != null && filter.minOrders() > 0 && row.orders() < filter.minOrders()) {
            return false;
        }
        String search = filter.search();
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.toLowerCase(Locale.ROOT).trim();
        return String.valueOf(row.customerId()).contains(normalized)
                || row.customerName().toLowerCase(Locale.ROOT).contains(normalized)
                || row.branchName().toLowerCase(Locale.ROOT).contains(normalized)
                || row.favoriteProduct().toLowerCase(Locale.ROOT).contains(normalized)
                || row.favoriteCategory().toLowerCase(Locale.ROOT).contains(normalized)
                || row.maskedPhone().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private Comparator<CustomerBehaviorRow> comparator(CustomerBehaviorFilter filter) {
        String sortBy = filter == null || filter.sortBy() == null ? "totalSpend" : filter.sortBy().trim();
        Comparator<CustomerBehaviorRow> comparator = switch (sortBy) {
            case "customer", "customerName" -> Comparator.comparing(CustomerBehaviorRow::customerName, String.CASE_INSENSITIVE_ORDER);
            case "branch", "branchName" -> Comparator.comparing(CustomerBehaviorRow::branchName, String.CASE_INSENSITIVE_ORDER);
            case "segment" -> Comparator.comparing(row -> row.segment().name());
            case "lastPurchase", "lastPurchaseAt" -> Comparator.comparing(CustomerBehaviorRow::lastPurchaseAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "daysInactive", "daysSinceLastPurchase" -> Comparator.comparing(CustomerBehaviorRow::daysSinceLastPurchase, Comparator.nullsLast(Comparator.naturalOrder()));
            case "orders" -> Comparator.comparingLong(CustomerBehaviorRow::orders);
            case "aov", "averageOrderValue" -> Comparator.comparing(CustomerBehaviorRow::averageOrderValue);
            case "favoriteCategory" -> Comparator.comparing(CustomerBehaviorRow::favoriteCategory, String.CASE_INSENSITIVE_ORDER);
            case "discountRatio" -> Comparator.comparing(CustomerBehaviorRow::discountRatio);
            case "returnRatio" -> Comparator.comparing(CustomerBehaviorRow::returnRatio);
            default -> Comparator.comparing(CustomerBehaviorRow::totalSpend);
        };
        String direction = filter == null || filter.sortDirection() == null ? "desc" : filter.sortDirection().trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(direction)) {
            return comparator.thenComparingLong(CustomerBehaviorRow::customerId);
        }
        return comparator.reversed().thenComparingLong(CustomerBehaviorRow::customerId);
    }

    private BigDecimal averageCadence(LocalDateTime firstPurchaseAt, LocalDateTime lastPurchaseAt, long historicalOrderCount) {
        if (firstPurchaseAt == null || lastPurchaseAt == null || historicalOrderCount <= 1) {
            return BigDecimal.ZERO;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(firstPurchaseAt.toLocalDate(), lastPurchaseAt.toLocalDate()));
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(historicalOrderCount - 1), 4, RoundingMode.HALF_UP);
    }

    private String deterministicCustomerRecommendation(CustomerBehaviorRow row, String locale) {
        boolean isAr = locale != null && locale.trim().toLowerCase().startsWith("ar");
        return switch (row.segment()) {
            case RETURN_RISK -> isAr 
                    ? "راجع المرتجعات الأخيرة قبل ترويج المزيد من المنتجات لهذا العميل."
                    : "Review recent returns before promoting more products to this customer.";
            case DORMANT -> isAr 
                    ? "قم بتشغيل رسالة استعادة مع عرض عملي مرتبط بالفئة السابقة للعميل."
                    : "Run a win-back message with a practical offer tied to the customer's past category.";
            case AT_RISK -> isAr 
                    ? "اتصل بالعميل قبل أن يصبح خاملاً وأوصِ بالمنتجات المفضلة لديه."
                    : "Contact the customer before they become dormant and recommend their preferred products.";
            case VIP -> isAr 
                    ? "حافظ على العلاقة مع خدمة ذات أولوية ووصول مبكر للمنتجات ذات الصلة."
                    : "Protect the relationship with priority service and early access to relevant products.";
            case LOYAL -> isAr 
                    ? "قدم باقة مستهدفة من الفئة المفضلة للعميل."
                    : "Offer a targeted bundle from the customer's favorite category.";
            case NEW -> isAr 
                    ? "شجع على عملية شراء ثانية بمتابعة بسيطة بناءً على السلة الأولى."
                    : "Encourage a second purchase with a simple follow-up based on the first basket.";
            case DISCOUNT_SENSITIVE -> isAr 
                    ? "استخدم عروضاً خاضعة للرقابة وتجنب الخصومات الشاملة غير الضرورية."
                    : "Use controlled offers and avoid unnecessary blanket discounts.";
            case CATEGORY_LOYAL -> isAr 
                    ? "روج لمجموعة منتجات أعمق في الفئة المفضلة للعميل."
                    : "Promote deeper assortment in the customer's preferred category.";
            case ACTIVE -> isAr 
                    ? "حافظ على تفاعل العميل مع توصيات المنتجات ذات الصلة."
                    : "Keep the customer engaged with relevant product recommendations.";
        };
    }

    private String label(CustomerSegment segment) {
        String[] words = segment.name().toLowerCase(Locale.ROOT).split("_");
        List<String> formatted = new ArrayList<>();
        for (String word : words) {
            formatted.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
        }
        return String.join(" ", formatted);
    }

    private ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Africa/Cairo" : timezone);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_BEHAVIOR_INVALID_TIMEZONE", "Configured timezone is invalid");
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
