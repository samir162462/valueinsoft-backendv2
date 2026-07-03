package com.example.valueinsoftbackend.companyinsights.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic ar/en rendering of insights from backend-owned slots. This output is the
 * source of truth and the default rendering when AI enrichment is disabled or fails.
 *
 * <p>Every rendered string is produced from validated slot values; no free text or numbers
 * originate outside the deterministic engine.
 */
@Component
public class CompanyInsightTemplateRenderer {

    /**
     * Rendered content for one insight: a default-locale (Arabic) title/description/summary
     * plus a localized map {@code { "ar": {...}, "en": {...} }}.
     */
    public record Rendered(String title, String description, String summary, Map<String, Object> localized) {
    }

    public Rendered weeklyPerformance(double salesChangePct,
                                      double marginChangePct,
                                      double ordersChangePct,
                                      double aovChangePct,
                                      double discountChangePct,
                                      double returnChangePct,
                                      String primaryConditionCode) {
        String arTitle;
        String enTitle;
        switch (primaryConditionCode) {
            case "SALES_UP_MARGIN_DOWN" -> {
                arTitle = "المبيعات ترتفع لكن هامش الربح ينخفض";
                enTitle = "Sales up but profit margin down";
            }
            case "SALES_DROP" -> {
                arTitle = "انخفاض ملموس في المبيعات هذا الأسبوع";
                enTitle = "Material sales drop this week";
            }
            case "ORDERS_UP_AOV_DOWN" -> {
                arTitle = "الطلبات ترتفع لكن متوسط قيمة الطلب ينخفض";
                enTitle = "Orders up but average order value down";
            }
            case "MARGIN_DROP" -> {
                arTitle = "انخفاض في هامش الربح الإجمالي";
                enTitle = "Gross margin decline this week";
            }
            default -> {
                arTitle = "ملخص أداء الشركة الأسبوعي";
                enTitle = "Company weekly performance summary";
            }
        }

        String arDesc = "مقارنة بالأسبوع السابق: المبيعات " + pctAr(salesChangePct)
                + "، هامش الربح " + pointsAr(marginChangePct)
                + "، الطلبات " + pctAr(ordersChangePct)
                + "، متوسط قيمة الطلب " + pctAr(aovChangePct)
                + "، الخصومات " + pctAr(discountChangePct)
                + "، المرتجعات " + pctAr(returnChangePct) + ".";

        String enDesc = "Versus last week: sales " + pctEn(salesChangePct)
                + ", gross margin " + pointsEn(marginChangePct)
                + ", orders " + pctEn(ordersChangePct)
                + ", average order value " + pctEn(aovChangePct)
                + ", discounts " + pctEn(discountChangePct)
                + ", returns " + pctEn(returnChangePct) + ".";

        String arSummary = "راجع تقرير الأرباح لتحديد الفروع والمنتجات الأكثر تأثيراً على الهامش هذا الأسبوع.";
        String enSummary = "Open the profit report to see which branches and products drove the margin this week.";

        Map<String, Object> localized = new LinkedHashMap<>();
        localized.put("ar", localeMap(arTitle, arDesc, arSummary));
        localized.put("en", localeMap(enTitle, enDesc, enSummary));
        return new Rendered(arTitle, arDesc, arSummary, localized);
    }

    public Rendered lowPerformingBranch(String branchName, double gapVsAveragePct, double gapVsBaselinePct) {
        String arTitle = "أداء منخفض للفرع: " + branchName;
        String enTitle = "Low-performing branch: " + branchName;
        String arDesc = "مبيعات فرع " + branchName + " أقل من متوسط الشركة بنسبة " + pctAr(gapVsAveragePct)
                + " وأقل من أدائه المعتاد بنسبة " + pctAr(gapVsBaselinePct) + " هذا الأسبوع.";
        String enDesc = branchName + " sales are " + pctEn(gapVsAveragePct) + " vs the company average and "
                + pctEn(gapVsBaselinePct) + " vs its own recent baseline this week.";
        String arSummary = "افتح تقرير أداء الفرع لتحديد الأسباب واتخاذ إجراء.";
        String enSummary = "Open the branch performance report to investigate and act.";
        return build(arTitle, arDesc, arSummary, enTitle, enDesc, enSummary);
    }

    public Rendered companyWideLowStock(long productId, String productName, int affectedBranches,
                                        double totalQty, boolean transferPossible, String branchList) {
        String label = productLabel(productName, productId);
        String arTitle = "نقص مخزون في عدة فروع: " + label;
        String enTitle = "Multi-branch low stock — " + label;
        String transferAr = transferPossible ? " يتوفر رصيد في فرع آخر يمكن تحويله." : "";
        String transferEn = transferPossible ? " Stock exists in another branch and could be transferred." : "";
        String branchAr = branchList == null || branchList.isBlank() ? "" : (" الفروع المتأثرة: " + branchList + ".");
        String branchEn = branchList == null || branchList.isBlank() ? "" : (" Affected branches: " + branchList + ".");
        String arDesc = "المنتج منخفض/منتهٍ في " + affectedBranches + " فرع، وإجمالي المتاح بالشركة "
                + format(totalQty) + "." + branchAr + transferAr;
        String enDesc = "Product is low/out in " + affectedBranches + " branch(es); total company quantity "
                + format(totalQty) + "." + branchEn + transferEn;
        String arSummary = "افتح صفحة المخزون لمراجعة النواقص واتخاذ قرار إعادة الطلب أو التحويل.";
        String enSummary = "Open inventory to review shortages and decide on reorder or transfer.";
        return build(arTitle, arDesc, arSummary, enTitle, enDesc, enSummary);
    }

    public Rendered deadStock(long productId, String productName, double totalValue, int branches,
                              String lastMovement, String branchList) {
        String label = productLabel(productName, productId);
        String lastAr = lastMovement == null ? "لا توجد حركة مسجلة" : ("آخر حركة " + lastMovement);
        String lastEn = lastMovement == null ? "no recorded movement" : ("last movement " + lastMovement);
        String branchAr = branchList == null || branchList.isBlank() ? "" : (" الفروع: " + branchList + ".");
        String branchEn = branchList == null || branchList.isBlank() ? "" : (" Branches: " + branchList + ".");
        String arTitle = "مخزون راكد: " + label;
        String enTitle = "Dead stock — " + label;
        String arDesc = "قيمة مخزون راكدة تقارب " + format(totalValue) + " موزعة على " + branches + " فرع (" + lastAr + ")." + branchAr;
        String enDesc = "About " + format(totalValue) + " in tied-up value across " + branches + " branch(es) (" + lastEn + ")." + branchEn;
        String arSummary = "افتح صفحة المخزون الراكد للتصرف في هذه الأصناف.";
        String enSummary = "Open dead-stock inventory to act on these items.";
        return build(arTitle, arDesc, arSummary, enTitle, enDesc, enSummary);
    }

    private String productLabel(String productName, long productId) {
        return productName == null || productName.isBlank() ? ("#" + productId) : (productName + " (#" + productId + ")");
    }

    public Rendered branchNoActivity(String branchName, int minutesSinceOpen) {
        String arTitle = "لا يوجد نشاط في فرع " + branchName;
        String enTitle = "No activity at branch " + branchName;
        String arDesc = "لم تُسجَّل أي مبيعات في فرع " + branchName + " بعد مرور " + minutesSinceOpen + " دقيقة من بدء العمل اليوم.";
        String enDesc = "No sales recorded at " + branchName + " after " + minutesSinceOpen + " minutes of today's business hours.";
        String arSummary = "افتح نقطة البيع للفرع للتحقق من التشغيل.";
        String enSummary = "Open the branch POS to verify operations.";
        return build(arTitle, arDesc, arSummary, enTitle, enDesc, enSummary);
    }

    private Rendered build(String arTitle, String arDesc, String arSummary,
                           String enTitle, String enDesc, String enSummary) {
        Map<String, Object> localized = new LinkedHashMap<>();
        localized.put("ar", localeMap(arTitle, arDesc, arSummary));
        localized.put("en", localeMap(enTitle, enDesc, enSummary));
        return new Rendered(arTitle, arDesc, arSummary, localized);
    }

    private Map<String, Object> localeMap(String title, String description, String summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("description", description);
        map.put("summary", summary);
        return map;
    }

    private String pctAr(double pct) {
        String sign = pct > 0 ? "+" : "";
        return sign + format(pct) + "%";
    }

    private String pctEn(double pct) {
        String sign = pct > 0 ? "+" : "";
        return sign + format(pct) + "%";
    }

    private String pointsAr(double pts) {
        String sign = pts > 0 ? "+" : "";
        return sign + format(pts) + " نقطة";
    }

    private String pointsEn(double pts) {
        String sign = pts > 0 ? "+" : "";
        return sign + format(pts) + " pts";
    }

    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
