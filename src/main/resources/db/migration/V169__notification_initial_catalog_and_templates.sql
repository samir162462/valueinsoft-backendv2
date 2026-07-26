-- Initial reviewed Notification Center catalog.
-- preview_reviewed_by = 0 identifies the migration-reviewed baseline; later edits use
-- an actual platform-admin user id through the template publishing service.

CREATE TEMP TABLE notification_seed (
    type_key TEXT,
    module_id TEXT,
    category TEXT,
    priority TEXT,
    preview_policy TEXT,
    group_key_template TEXT,
    aggregation_window_seconds INTEGER,
    deep_link_template TEXT,
    required_capability TEXT,
    is_user_mutable BOOLEAN,
    bypasses_quiet_hours BOOLEAN,
    retention_days INTEGER,
    title_en TEXT,
    body_en TEXT,
    preview_en TEXT,
    title_ar TEXT,
    body_ar TEXT,
    preview_ar TEXT
) ON COMMIT DROP;

INSERT INTO notification_seed VALUES
('inventory.stock.low','inventory','operational','high','allowed','stock:{branchId}:{productId}',3600,'valueinsoft://inventory/products/{productId}','inventory.item.read',TRUE,FALSE,180,'Low stock','{productName} is below its stock threshold.','A low-stock item needs attention','مخزون منخفض','انخفض مخزون {productName} عن الحد المحدد.','يوجد صنف منخفض المخزون'),
('inventory.stock.out','inventory','operational','critical','allowed','stockout:{branchId}:{productId}',1800,'valueinsoft://inventory/products/{productId}','inventory.item.read',FALSE,TRUE,365,'Out of stock','{productName} is out of stock.','An item is out of stock','نفاد المخزون','نفد مخزون {productName}.','يوجد صنف نافد المخزون'),
('inventory.stock.reorder','inventory','operational','normal','allowed','reorder:{branchId}',7200,'valueinsoft://inventory/reorder','inventory.item.read',TRUE,FALSE,180,'Reorder suggested','Inventory items are ready for reorder review.','Inventory needs a reorder review','اقتراح إعادة طلب','توجد أصناف جاهزة لمراجعة إعادة الطلب.','المخزون يحتاج مراجعة إعادة الطلب'),
('inventory.adjustment.posted','inventory','operational','normal','generic_only',NULL,0,'valueinsoft://inventory/adjustments/{adjustmentId}','inventory.adjustment.create',TRUE,FALSE,180,'Adjustment posted','Inventory adjustment {adjustmentId} was posted.','An inventory adjustment was posted','تم ترحيل التسوية','تم ترحيل تسوية المخزون {adjustmentId}.','تم ترحيل تسوية للمخزون'),
('inventory.transfer.requested','inventory','operational','normal','generic_only','transfer-request:{destinationBranchId}',3600,'valueinsoft://inventory/transfers/{transferId}','inventory.item.read',TRUE,FALSE,180,'Transfer requested','An inventory transfer requires review.','An inventory transfer needs review','طلب تحويل مخزون','يوجد تحويل مخزون يحتاج إلى المراجعة.','تحويل مخزون يحتاج للمراجعة'),
('inventory.transfer.received','inventory','operational','normal','generic_only',NULL,0,'valueinsoft://inventory/transfers/{transferId}','inventory.item.read',TRUE,FALSE,180,'Transfer received','Inventory transfer {transferId} was received.','An inventory transfer was received','تم استلام التحويل','تم استلام تحويل المخزون {transferId}.','تم استلام تحويل مخزون'),

('pos.order.voided','pos','operational','high','generic_only',NULL,0,'valueinsoft://pos/orders/{orderId}','pos.sale.create',FALSE,FALSE,365,'Order voided','Order {orderId} was voided.','A sales order was voided','تم إلغاء الطلب','تم إلغاء الطلب {orderId}.','تم إلغاء طلب مبيعات'),
('pos.refund.created','pos','financial','high','generic_only',NULL,0,'valueinsoft://pos/refunds/{refundId}','pos.sale.create',FALSE,FALSE,1095,'Refund created','Refund {refundId} was created.','A refund was created','تم إنشاء مرتجع','تم إنشاء المرتجع {refundId}.','تم إنشاء عملية مرتجع'),
('pos.shift.opened','pos','operational','low','generic_only',NULL,0,'valueinsoft://pos/shifts/{shiftId}','pos.sale.create',TRUE,FALSE,90,'Shift opened','Shift {shiftId} was opened.','A POS shift was opened','تم فتح الوردية','تم فتح الوردية {shiftId}.','تم فتح وردية نقاط البيع'),
('pos.shift.closed','pos','operational','normal','generic_only',NULL,0,'valueinsoft://pos/shifts/{shiftId}','pos.sale.create',TRUE,FALSE,180,'Shift closed','Shift {shiftId} was closed.','A POS shift was closed','تم إغلاق الوردية','تم إغلاق الوردية {shiftId}.','تم إغلاق وردية نقاط البيع'),
('pos.cash.variance','pos','financial','high','generic_only','cash-variance:{branchId}',3600,'valueinsoft://pos/shifts/{shiftId}','finance.entry.read',FALSE,FALSE,1095,'Cash variance','A closed shift has a cash variance requiring review.','A cash variance needs review','فرق نقدي','توجد وردية مغلقة بها فرق نقدي يحتاج للمراجعة.','يوجد فرق نقدي يحتاج للمراجعة'),
('pos.sale.large','pos','financial','normal','generic_only','large-sale:{branchId}',1800,'valueinsoft://pos/orders/{orderId}','finance.entry.read',TRUE,FALSE,365,'Large sale recorded','A sale exceeded the configured review threshold.','A large sale was recorded','عملية بيع كبيرة','تجاوزت عملية بيع حد المراجعة المحدد.','تم تسجيل عملية بيع كبيرة'),

('finance.invoice.overdue','finance','financial','high','generic_only','invoice-overdue:{companyId}',86400,'valueinsoft://finance/invoices/{invoiceId}','finance.entry.read',TRUE,FALSE,1095,'Invoice overdue','Invoice {invoiceNumber} is overdue.','An invoice is overdue','فاتورة متأخرة','الفاتورة {invoiceNumber} متأخرة.','توجد فاتورة متأخرة'),
('finance.payment.received','finance','financial','normal','generic_only',NULL,0,'valueinsoft://finance/payments/{paymentId}','finance.entry.read',TRUE,FALSE,1095,'Payment received','Payment {paymentId} was received.','A payment was received','تم استلام دفعة','تم استلام الدفعة {paymentId}.','تم استلام دفعة'),
('finance.payment.failed','finance','financial','critical','generic_only','payment-failed:{companyId}',1800,'valueinsoft://finance/payments/{paymentId}','finance.entry.read',FALSE,TRUE,1095,'Payment failed','A payment failed and requires immediate review.','A payment failed','فشل الدفع','فشلت عملية دفع وتحتاج إلى مراجعة فورية.','فشلت عملية دفع'),
('finance.expense.approved','finance','financial','normal','generic_only',NULL,0,'valueinsoft://finance/expenses/{expenseId}','finance.entry.read',TRUE,FALSE,1095,'Expense approved','Expense {expenseId} was approved.','An expense was approved','تم اعتماد المصروف','تم اعتماد المصروف {expenseId}.','تم اعتماد مصروف'),
('finance.expense.rejected','finance','financial','normal','generic_only',NULL,0,'valueinsoft://finance/expenses/{expenseId}','finance.entry.read',TRUE,FALSE,1095,'Expense rejected','Expense {expenseId} was rejected.','An expense was rejected','تم رفض المصروف','تم رفض المصروف {expenseId}.','تم رفض مصروف'),
('finance.cash.closing_failed','finance','financial','high','generic_only','cash-close:{branchId}',3600,'valueinsoft://finance/cash-closing/{closingId}','finance.entry.read',FALSE,FALSE,1095,'Cash closing failed','Daily cash closing requires attention.','Cash closing needs attention','فشل الإغلاق النقدي','يحتاج الإغلاق النقدي اليومي إلى المراجعة.','الإغلاق النقدي يحتاج للمراجعة'),

('clients.credit.limit_reached','clients','financial','high','generic_only','client-credit:{clientId}',7200,'valueinsoft://clients/{clientId}/credit','clients.credit.view',TRUE,FALSE,1095,'Credit limit reached','A client account reached its credit limit.','A client credit limit was reached','تم بلوغ حد الائتمان','بلغ حساب عميل حد الائتمان.','تم بلوغ حد ائتمان عميل'),
('clients.payment.overdue','clients','financial','high','generic_only','client-overdue:{clientId}',86400,'valueinsoft://clients/{clientId}/open-items','clients.openitems.view',TRUE,FALSE,1095,'Client payment overdue','A client payment is overdue.','A client payment is overdue','دفعة عميل متأخرة','توجد دفعة عميل متأخرة.','توجد دفعة عميل متأخرة'),
('clients.account.created','clients','system','low','generic_only',NULL,0,'valueinsoft://clients/{clientId}','clients.account.read',TRUE,FALSE,180,'Client account created','A new client account was created.','A client account was created','تم إنشاء حساب عميل','تم إنشاء حساب عميل جديد.','تم إنشاء حساب عميل'),

('suppliers.purchase_order.approved','suppliers','financial','normal','generic_only',NULL,0,'valueinsoft://suppliers/purchase-orders/{purchaseOrderId}','suppliers.account.read',TRUE,FALSE,1095,'Purchase order approved','Purchase order {purchaseOrderId} was approved.','A purchase order was approved','تم اعتماد أمر الشراء','تم اعتماد أمر الشراء {purchaseOrderId}.','تم اعتماد أمر شراء'),
('suppliers.delivery.late','suppliers','operational','high','generic_only','supplier-late:{supplierId}',86400,'valueinsoft://suppliers/{supplierId}/orders','suppliers.account.read',TRUE,FALSE,365,'Supplier delivery late','A supplier delivery is late.','A supplier delivery is late','توريد متأخر','يوجد توريد من مورد متأخر.','يوجد توريد متأخر'),
('suppliers.payment.due','suppliers','financial','normal','generic_only','supplier-due:{supplierId}',86400,'valueinsoft://suppliers/{supplierId}/open-items','suppliers.openitems.view',TRUE,FALSE,1095,'Supplier payment due','A supplier payment is due.','A supplier payment is due','دفعة مورد مستحقة','توجد دفعة مورد مستحقة.','توجد دفعة مورد مستحقة'),

('hr.leave.requested','attendance','hr','normal','generic_only','leave-request:{managerUserId}',3600,'valueinsoft://hr/leave/{leaveRequestId}','hr.leave.manage',TRUE,FALSE,1095,'Leave requested','A leave request needs review.','A leave request needs review','طلب إجازة','يوجد طلب إجازة يحتاج للمراجعة.','طلب إجازة يحتاج للمراجعة'),
('hr.leave.approved','attendance','hr','normal','generic_only',NULL,0,'valueinsoft://hr/leave/{leaveRequestId}','hr.leave.self',TRUE,FALSE,1095,'Leave approved','Your leave request was approved.','A leave request was approved','تم اعتماد الإجازة','تم اعتماد طلب الإجازة.','تم اعتماد طلب إجازة'),
('hr.leave.rejected','attendance','hr','normal','generic_only',NULL,0,'valueinsoft://hr/leave/{leaveRequestId}','hr.leave.self',TRUE,FALSE,1095,'Leave rejected','Your leave request was rejected.','A leave request was rejected','تم رفض الإجازة','تم رفض طلب الإجازة.','تم رفض طلب إجازة'),
('hr.attendance.late','attendance','hr','normal','generic_only','late:{userId}',86400,'valueinsoft://hr/attendance/{attendanceDate}','attendance.self.read',TRUE,FALSE,1095,'Late attendance','A late attendance record was detected.','An attendance item needs review','تأخر في الحضور','تم رصد حالة تأخر في الحضور.','سجل حضور يحتاج للمراجعة'),
('hr.attendance.missed','attendance','hr','high','generic_only','missed:{userId}',86400,'valueinsoft://hr/attendance/{attendanceDate}','attendance.manage',TRUE,FALSE,1095,'Attendance missing','An expected attendance record is missing.','An attendance record is missing','سجل حضور مفقود','يوجد سجل حضور متوقع مفقود.','يوجد سجل حضور مفقود'),
('hr.payroll.ready','payroll','hr','normal','generic_only',NULL,0,'valueinsoft://hr/payroll/{payrollRunId}','payroll.run.view',TRUE,FALSE,2555,'Payroll ready','A payroll run is ready for review.','A payroll run is ready','كشف الرواتب جاهز','دورة رواتب جاهزة للمراجعة.','دورة رواتب جاهزة'),
('hr.payroll.failed','payroll','hr','high','generic_only','payroll-failed:{companyId}',3600,'valueinsoft://hr/payroll/{payrollRunId}','payroll.run.manage',FALSE,FALSE,2555,'Payroll failed','A payroll run failed and needs review.','A payroll run failed','فشلت دورة الرواتب','فشلت دورة رواتب وتحتاج للمراجعة.','فشلت دورة رواتب'),

('security.login.new_device','users','security','critical','generic_only','new-device:{userId}',3600,'valueinsoft://profile/security','profile.self.read',FALSE,TRUE,1095,'New device sign-in','A new device signed in to your account.','A new device signed in','تسجيل دخول من جهاز جديد','تم تسجيل الدخول إلى حسابك من جهاز جديد.','تم تسجيل دخول من جهاز جديد'),
('security.login.failed','users','security','high','generic_only','login-failed:{userId}',1800,'valueinsoft://profile/security','profile.self.read',FALSE,FALSE,1095,'Failed sign-in attempts','Multiple failed sign-in attempts were detected.','Failed sign-in attempts detected','محاولات دخول فاشلة','تم رصد عدة محاولات تسجيل دخول فاشلة.','تم رصد محاولات دخول فاشلة'),
('security.password.changed','users','security','high','generic_only',NULL,0,'valueinsoft://profile/security','profile.self.read',FALSE,FALSE,1095,'Password changed','Your account password was changed.','An account password was changed','تم تغيير كلمة المرور','تم تغيير كلمة مرور حسابك.','تم تغيير كلمة مرور الحساب'),
('security.role.changed','users','security','high','generic_only',NULL,0,'valueinsoft://profile/security','profile.self.read',FALSE,FALSE,1095,'Access changed','Your role or access permissions changed.','Account access was changed','تم تغيير الصلاحيات','تم تغيير دورك أو صلاحيات الوصول.','تم تغيير صلاحيات الحساب'),

('system.integration.failed','web_admin','system','high','generic_only','integration:{integrationKey}',3600,'valueinsoft://admin/integrations/{integrationKey}','platform.support.read',FALSE,FALSE,365,'Integration failed','An external integration failed.','An integration needs attention','فشل التكامل','فشل تكامل خارجي.','تكامل خارجي يحتاج للمراجعة'),
('system.backup.failed','web_admin','system','critical','generic_only','backup:{companyId}',3600,'valueinsoft://admin/system/backups','platform.support.read',FALSE,TRUE,1095,'Backup failed','A scheduled backup failed.','A backup failed','فشل النسخ الاحتياطي','فشلت عملية نسخ احتياطي مجدولة.','فشلت عملية نسخ احتياطي'),
('system.subscription.expiring','company_settings','system','high','generic_only','subscription:{companyId}',86400,'valueinsoft://company/subscription','company.settings.read',FALSE,FALSE,365,'Subscription expiring','The company subscription is nearing expiry.','A subscription is expiring','قرب انتهاء الاشتراك','اقترب موعد انتهاء اشتراك الشركة.','اشتراك يقترب من الانتهاء'),
('system.notification.dead_letter','web_admin','system','high','generic_only','dead-letter:{provider}',3600,'valueinsoft://admin/notifications/dead-letters','notification.admin.view',FALSE,FALSE,365,'Notification delivery failed','Notification deliveries exhausted their retries.','Notification delivery needs attention','فشل تسليم إشعارات','استنفدت عمليات تسليم إشعارات محاولات الإعادة.','تسليم إشعارات يحتاج للمراجعة'),
('marketing.campaign.published','dashboard','marketing','low','allowed','campaign:{campaignId}',86400,'valueinsoft://campaigns/{campaignId}',NULL,TRUE,FALSE,90,'Campaign published','Campaign {campaignName} was published.','A new campaign is available','تم نشر حملة','تم نشر الحملة {campaignName}.','توجد حملة جديدة');

INSERT INTO public.notification_type_catalog (
    type_key, module_id, category, default_priority, push_preview_policy,
    group_key_template, aggregation_window_seconds, deep_link_template,
    required_capability, is_user_mutable, bypasses_quiet_hours, retention_days,
    preview_max_chars, producer_rate_limit_per_min, status
)
SELECT
    type_key, module_id, category, priority, preview_policy,
    group_key_template, aggregation_window_seconds, deep_link_template,
    required_capability, is_user_mutable, bypasses_quiet_hours, retention_days,
    120, 120, 'active'
FROM notification_seed
ON CONFLICT (type_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    category = EXCLUDED.category,
    default_priority = EXCLUDED.default_priority,
    push_preview_policy = EXCLUDED.push_preview_policy,
    group_key_template = EXCLUDED.group_key_template,
    aggregation_window_seconds = EXCLUDED.aggregation_window_seconds,
    deep_link_template = EXCLUDED.deep_link_template,
    required_capability = EXCLUDED.required_capability,
    is_user_mutable = EXCLUDED.is_user_mutable,
    bypasses_quiet_hours = EXCLUDED.bypasses_quiet_hours,
    retention_days = EXCLUDED.retention_days,
    preview_max_chars = EXCLUDED.preview_max_chars,
    producer_rate_limit_per_min = EXCLUDED.producer_rate_limit_per_min,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO public.notification_template (
    type_key, locale, template_version, title_template, body_template,
    preview_template, preview_generic, preview_reviewed_by, preview_reviewed_at,
    status, published_at, created_by
)
SELECT type_key, 'en', 1, title_en, body_en, preview_en, preview_en,
       0, TIMESTAMPTZ '2026-07-25 00:00:00+00', 'published',
       TIMESTAMPTZ '2026-07-25 00:00:00+00', 0
FROM notification_seed
UNION ALL
SELECT type_key, 'ar', 1, title_ar, body_ar, preview_ar, preview_ar,
       0, TIMESTAMPTZ '2026-07-25 00:00:00+00', 'published',
       TIMESTAMPTZ '2026-07-25 00:00:00+00', 0
FROM notification_seed
ON CONFLICT (type_key, locale, template_version) DO UPDATE SET
    title_template = EXCLUDED.title_template,
    body_template = EXCLUDED.body_template,
    preview_template = EXCLUDED.preview_template,
    preview_generic = EXCLUDED.preview_generic,
    preview_reviewed_by = EXCLUDED.preview_reviewed_by,
    preview_reviewed_at = EXCLUDED.preview_reviewed_at,
    status = EXCLUDED.status,
    published_at = EXCLUDED.published_at;

DO $$
DECLARE
    seeded_types INTEGER;
    seeded_templates INTEGER;
BEGIN
    SELECT COUNT(*) INTO seeded_types FROM notification_seed;
    SELECT COUNT(*) INTO seeded_templates
    FROM public.notification_template t
    JOIN notification_seed s USING (type_key)
    WHERE t.template_version = 1 AND t.locale IN ('en','ar') AND t.status = 'published';

    IF seeded_types <> 40 OR seeded_templates <> 80 THEN
        RAISE EXCEPTION 'Notification seed incomplete: % types, % published templates',
            seeded_types, seeded_templates;
    END IF;
END;
$$;

