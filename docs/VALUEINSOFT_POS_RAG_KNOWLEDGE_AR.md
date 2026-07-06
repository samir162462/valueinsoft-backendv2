# معرفة وحدة نقاط البيع POS في ValueInSoft (RAG)

الجمهور المستهدف: المساعد الذكي، موظف الدعم، مطوّر الـ backend.

الغرض: وثيقة معرفة جاهزة للـ RAG تخص نطاق نقاط البيع (POS) في ValueInSoft. تشرح ما تفعله وحدة POS، ومسارات الـ REST الخاصة بها، والصلاحيات المطلوبة، ونماذج الطلب/الاستجابة، وسير عملية البيع وآثارها الجانبية، ودورة حياة الوردية، والجداول الأساسية في قاعدة البيانات. قسّم هذه الوثيقة حسب عناوين `##`.

ملاحظة النطاق: ValueInSoft منصة ERP/POS متعددة المستأجرين (multi-tenant). المستأجر (tenant) هو شركة (`companyId`)، والشركة تملك فرعًا أو أكثر (`branchId`). وحدة POS هي وحدة البيع في الخط الأمامي، وترتبط بالمخزون (Inventory) والمالية (Finance) والولاء (Loyalty) وإدارة الورديات والنقد (Shift/Cash). تركّز هذه الوثيقة على POS. للاطلاع على تفاصيل SQL/المخطط وقواعد الترحيل (migrations) راجع `VALUEINSOFT_SQL_RAG_KNOWLEDGE.md`.

## نظرة عامة على وحدة POS

تتيح وحدة POS للكاشير تسجيل المبيعات، وإدارة كتالوج المنتجات والفئات، وتشغيل العروض الترويجية، وفتح وإغلاق ورديات النقد، والتعامل مع البضاعة التالفة/المرتجعة، وتعديل المخزون. كل إجراء في POS مرتبط بـ `companyId` و `branchId`، ومُوثَّق عبر JWT (`Principal`)، ومُصرَّح به عبر فحص صلاحية دقيق قبل تنفيذ أي عمل.

المفاهيم الأساسية في POS:

- الطلب (البيع): فاتورة بإجماليات في الرأس وسطر واحد أو أكثر من الأصناف. البيع يخصم من المخزون، وللمبيعات النقدية يسجّل حركة نقد في الوردية ويضع قيدًا ماليًا في قائمة الانتظار.
- المنتج: صنف في الكتالوج. تُتابَع كمية المخزون وهوية المنتج بشكل منفصل. الأصناف عالية القيمة (الهواتف، الأجهزة) يمكن تتبّعها فرديًا عبر الرقم التسلسلي/IMEI.
- الفئة: تجميع هرمي للمنتجات، يُخزَّن لكل فرع كـ JSON، ويُهيّأ مبدئيًا من الباقة التجارية (business package) المخصّصة.
- العرض (Offer): خصم/حزمة ترويجية تُضبط لكل فرع.
- الوردية (Shift): جلسة نقدية للكاشير برصيد افتتاحي، وحركات نقدية، وإغلاق مُسوّى (reconciled).
- الصنف التالف / الإرجاع (bounce-back): تعديلات للبضاعة المكسورة أو سطور البيع المرتجعة.

## التوثيق والصلاحيات (Authorization And Capabilities)

كل دالة في متحكّمات POS تستدعي `authorizationService.assertAuthenticatedCapability(username, companyId, branchId, capabilityKey)` قبل تنفيذ أي عمل. أي صلاحية مفقودة أو نطاق خاطئ يُرفض. الصلاحيات مُعرّفة بالبيانات (تُزرَع عبر Flyway في `platform_capabilities` / `role_grants`)، ولا تُكتب في Java أو الواجهة الأمامية فقط.

مفاتيح صلاحيات POS المستخدمة:

- `pos.sale.create` — إنشاء/حفظ طلب (تسجيل بيع).
- `pos.sale.read` — قراءة الطلبات وتفاصيلها والعروض وطلبات الوردية.
- `pos.sale.edit` — تعديل المبيعات، إرجاع منتج، حفظ/حذف العروض.
- `pos.shift.create` — فتح/بدء وردية.
- `pos.shift.read` — قراءة الوردية النشطة والتسوية والأحداث وورديات الفرع.
- `pos.shift.edit` — تسجيل حركة نقدية، إنهاء الوردية القديم (legacy).
- `pos.shift.close` — إغلاق وردية مع التسوية.
- `pos.shift.force_close` — إغلاق قسري للوردية (إجراء المدير).

صلاحيات المخزون المرتبطة بنقاط النهاية المجاورة لـ POS:

- `inventory.item.read` — قراءة المنتجات والفئات وأسماء المنتجات والقوالب والوحدات المُسلسَلة.
- `inventory.item.create` — إنشاء منتج أو فئة.
- `inventory.item.edit` — تعديل منتج، تغيير نوع التتبّع، تعديل معرّفات الوحدة المُسلسَلة.
- `inventory.adjustment.create` — إضافة صنف تالف، إضافة حركة مخزون، إدخال/تحويل وحدات مُسلسَلة.
- `inventory.adjustment.edit` — تسوية/حذف صنف تالف.

قاعدة النطاق: الصلاحيات المرتبطة بالفرع تتطلب `branchId` صحيحًا؛ أما القراءات على مستوى الشركة (مثل الفئات الرئيسية والباقة التجارية) فتمرّر `branchId = null`.

## واجهة الطلبات (`/Order`)

المسار الأساسي `/Order`. يعالجها `OrderController` ← `OrderService` / `DbPosOrder`.

- `POST /Order/{companyId}/saveOrder` — إنشاء طلب. الصلاحية `pos.sale.create`. الجسم `CreateOrderRequest`. يُرجِع `201` مع `orderId` الجديد (عدد صحيح). شكل استجابة قديم/بسيط.
- `POST /Order/v2/pos/{companyId}/orders` — إنشاء طلب (الإصدار v2). الصلاحية `pos.sale.create`. الجسم `CreateOrderRequest`. يُرجِع `CreateOrderResult` كاملًا؛ الحالة `201` عند الإدراج الجديد، و `200` عند تطابق مفتاح الـ idempotency مع طلب موجود (idempotency hit).
- `POST /Order/getOrders` و `POST /Order/{companyId}/getOrders` — سرد الطلبات لفترة. الصلاحية `pos.sale.read`. الجسم `OrderPeriodRequest` (يتضمّن `branchId`). قد يأتي `companyId` من المسار أو من `?companyId=`.
- `GET /Order/getOrdersByClientId/{companyId}/{branchId}/{clientId}` — طلبات عميل واحد. الصلاحية `pos.sale.read`.
- `GET /Order/{companyId}/search/{branchId}?q={receiptNumber}` — إيجاد طلب واحد برقم الفاتورة. الصلاحية `pos.sale.read`. يُرجِع `404` إن لم يوجد.
- `GET /Order/getOrdersDetailsByOrderId/{companyId}/{branchId}/{orderId}` — سطور الطلب. الصلاحية `pos.sale.read`.
- `POST /Order/{companyId}/bounceBackProduct` — إرجاع/إعادة منتج مباع. الصلاحية `pos.sale.edit`. الجسم `BounceBackOrderRequest`.

### حقول CreateOrderRequest

`CreateOrderRequest` سجل (record) مُتحقَّق منه:

- `orderId` (int, ≥0) — القيمة 0 لطلب جديد.
- `orderTime` (نص) — طابع زمني من العميل؛ يُحلَّل في الخادم.
- `clientName` (نص) — اسم العميل العابر أو اسم العميل المرتبط.
- `orderType` (نص، مطلوب) — نوع البيع. قيم البيع النقدي القياسي: `Dirict` أو `Direct` أو بالعربية `مباشر`. الأنواع الأخرى (مثل الآجل/حساب العميل) تتخطّى خطوة الحركة النقدية.
- `orderDiscount` (int, ≥0) — خصم على مستوى الطلب.
- `orderTotal` (int, ≥0) — الإجمالي النهائي المُحصَّل.
- `salesUser` (نص، مطلوب) — اسم مستخدم الكاشير.
- `branchId` (int, موجب) — فرع البيع.
- `clientId` (int, ≥0) — 0 للعميل العابر؛ >0 يربط بـ `Client`.
- `orderIncome` (int, ≥0) — قيمة الربح/الهامش.
- `orderDetails` (قائمة `OrderItemRequest`، غير فارغة) — سطور الأصناف.
- `loyaltyRedemptionId`، `loyaltyPointsRedeemed`، `loyaltyPointsEarned`، `loyaltyDiscountAmount`، `loyaltyNetAmount` — حقول ولاء اختيارية.
- `idempotencyKey` (نص ≤255، اختياري) — رمز إعادة محاولة آمن؛ يُفضَّل UUID. عند إعادة إرسال المفتاح نفسه يُرجِع الخادم الطلب الأصلي بدل إنشاء نسخة مكررة.

يحمل نموذج `Order` إضافةً `receiptNumber`، و `requestedShiftId`، و `totalBouncedBack`، وقائمة `orderDetails` من نوع `OrderDetails`.

## سير عملية البيع وآثارها الجانبية

تُنفَّذ `OrderService.createOrder` ضمن وحدة `@Transactional` واحدة. التسلسل:

1. التحقق من `companyId` وتحويل الطلب إلى `Order`.
2. ترحيل البيع عبر `PosSalePostingService.postSale` (المسار الرسمي)، الذي يحفظ رأس الطلب + السطور ويخصم من المخزون. النتيجة `CreateOrderResult` تتضمّن `orderId` و `receiptNumber` و `shiftId` و `idempotencyHit`.
3. إذا كانت `idempotencyHit` صحيحة، يُتخطّى الترحيل اللاحق وتُعاد النتيجة الأصلية (بدون آثار مكررة على المخزون/المالية/النقد).
4. الولاء: تأكيد أي استبدال (`loyaltyRedemptionId > 0`) وتسجيل النقاط المكتسبة.
5. تحديد الوردية النشطة `shiftId` (من النتيجة، وإلا البحث عن الوردية النشطة للفرع).
6. الحركة النقدية: للبيع النقدي القياسي (`orderType` من `Dirict`/`Direct`/`مباشر`) بـ `orderTotal > 0` مع وردية نشطة، تُدرَج حركة `CASH_SALE` مرتبطة بالطلب.
7. المالية: يُوضَع قيد مالي للبيع في قائمة الانتظار *بعد الالتزام (after commit)* بحيث لا تُرحَّل القيود إلا عند نجاح المعاملة.

الثوابت الأساسية:

- الـ idempotency: لا تُنشئ طلبًا رسميًا ثانيًا لنفس `idempotencyKey`. إعادة المحاولة يجب ألا تُكرّر آثار المخزون أو النقد أو المالية.
- المخزون والهوية منفصلان: البيع يخصم من رصيد/وحدات المخزون، ولا يُعدّل هوية المنتج الأساسية.
- الأصناف المُسلسَلة (IMEI/serial) تُستهلَك كوحدات، لا كمجرد خصم كمية.
- القيود المالية تُوضَع في قائمة الانتظار بعد الالتزام، ويجب أن تبقى المدين = الدائن عند حدود الترحيل.

## واجهة المنتجات (`/products`)

المسار الأساسي `/products`. يعالجها `ProductController` ← `ProductService` / خدمات التسلسل والقوالب. المنتجات هي الكتالوج المشترك خلف البيع في POS والمخزون.

- `GET /products/search/{searchType}/{companyId}/{branchId}/{text}/{selectedPageNumber}` — بحث في الكتالوج. الصلاحية `inventory.item.read`. `searchType`: `dir` (كلمات نص حر)، `comName` (اسم الشركة/المنتج)، `Barcode` (باركود مطابق). حجم الصفحة 10.
- `POST /products/search/{searchType}/{companyId}/{branchId}/{text}/filter/{pageNumber}` — بحث مُفلتَر بجسم `ProductFilter`. `searchType`: `dir` أو `comName` أو `allData` (النطاق الكامل).
- `GET /products/{companyId}/{branchId}/{productId}` — جلب منتج واحد. الصلاحية `inventory.item.read`.
- `POST /products/{companyId}/{branchId}/saveProduct` — إنشاء منتج. الصلاحية `inventory.item.create`. الجسم `Product`. يُرجِع `201` مع `ProductOperationResponse`.
- `PUT /products/{companyId}/{branchId}/editProduct` — تعديل منتج. الصلاحية `inventory.item.edit`.
- `PUT /products/{companyId}/{branchId}/{productId}/tracking` — تغيير نوع التتبّع (مثلًا كمية ↔ مُسلسَل). الصلاحية `inventory.item.edit`. الجسم `ProductTrackingTypeChangeRequest`.
- `GET /products/{companyId}/{branchId}/{productId}/tracking` — قراءة بيانات التتبّع. الصلاحية `inventory.item.read`.
- `GET /products/PN/{companyId}/{branchId}/{text}` — إكمال تلقائي لأسماء المنتجات. الصلاحية `inventory.item.read`.
- `GET /products/{companyId}/{branchId}/templates` — قوالب المنتجات للشركة. الصلاحية `inventory.item.read`.
- `POST /products/export/excel` و `POST /products/export/pdf` — بثّ تصدير للكتالوج (`ProductCatalogExportRequest`)؛ اسم الملف `inventory-catalog-branch-{branchId}.{ext}`.

حقول الأسعار القديمة على `PosProduct`: `rPrice` (بيع التجزئة)، `lPrice` (آخر/جملة)، `bPrice` (الشراء/التكلفة)، `serial`، `quantity`، `pState`، `branchId`.

## واجهة الفئات (`/Categories`)

المسار الأساسي `/Categories`. يعالجها `CategoryController` ← `CategoryService`. الفئات تجمّع المنتجات لكل فرع وتُخزَّن كـ JSON، وتُهيّأ من الباقة التجارية المخصّصة للمستأجر.

- `POST /Categories/{companyId}/{branchId}/saveCategory` — إنشاء/تحديث فئة. الصلاحية `inventory.item.create`. الجسم `SaveCategoryRequest`.
- `GET /Categories/getCategoryJson/{companyId}/{branchId}` — الفئات كقائمة `CustomPair`. الصلاحية `inventory.item.read`.
- `GET /Categories/getCategoryJsonFlat/{companyId}/{branchId}` — نص JSON مسطّح للفئات.
- `GET /Categories/getMainMajors/{companyId}` — الفئات الرئيسية على مستوى الشركة (`MainMajor`). `branchId = null`.
- `GET /Categories/business-package/{companyId}` — الباقة التجارية المخصّصة للمستأجر (`BusinessPackageConfig`)، التي تُهيّئ الفئات الافتراضية.

## واجهة العروض (`/Offer`)

المسار الأساسي `/Offer`. يعالجها `OfferController` ← `DbPosOffer`. العروض خصومات/حزم ترويجية لكل فرع تُطبَّق عند البيع.

- `GET /Offer/{companyId}/{branchId}/offers` — سرد العروض. الصلاحية `pos.sale.read`.
- `POST /Offer/{companyId}/saveOffer` — إنشاء/تحديث عرض. الصلاحية `pos.sale.edit`. الجسم `Offer` (يحمل `branchId`). يُرجِع `201` مع معرّف العرض.
- `DELETE /Offer/{companyId}/{branchId}/deleteOffer/{offerId}` — حذف عرض. الصلاحية `pos.sale.edit`.

## واجهة الأصناف التالفة (`/DamagedItem`)

المسار الأساسي `/DamagedItem`. يعالجها `DamagedItemController` ← `DamagedItemService`. تتبّع البضاعة المكسورة/غير القابلة للبيع وتسويتها. لاحظ أن متغيّر المسار الأول اسمه `companyName` لكنه رقم `companyId`.

- `GET /DamagedItem/{companyId}/{branchId}/all` — سرد الأصناف التالفة. الصلاحية `inventory.item.read`.
- `POST /DamagedItem/{companyId}/{branchId}/add` — تسجيل صنف تالف. الصلاحية `inventory.adjustment.create`. الجسم `CreateDamagedItemRequest`. يُرجِع `202`.
- `PUT /DamagedItem/{companyId}/{branchId}/settle/{DId}` — تسوية صنف تالف. الصلاحية `inventory.adjustment.edit`. يُرجِع `{ "settled": true|false }`.
- `DELETE /DamagedItem/{companyId}/{branchId}/delete/{DId}` — حذف سجل صنف تالف. الصلاحية `inventory.adjustment.edit`. يُرجِع `{ "deleted": true|false }`.

## واجهة حركات المخزون والوحدات المُسلسَلة (`/invTrans`)

المسار الأساسي `/invTrans`. يعالجها `InventoryTransactionController` ← `InventoryTransactionService` / `SerializedInventoryService`. تتعامل مع تعديلات المخزون والوحدات المُتتبَّعة بـ IMEI/serial المستخدمة في POS.

- `POST /invTrans/AddTransaction` — إضافة حركة مخزون. الصلاحية `inventory.adjustment.create`. الجسم `CreateInventoryTransactionRequest`. يُرجِع `201`.
- `POST /invTrans/transactions` — الاستعلام عن الحركات. الصلاحية `inventory.item.read`. الجسم `InventoryTransactionQueryRequest`.
- `POST /invTrans/AddSerializedStockIn` — إدخال وحدات مُسلسَلة إلى المخزون. الصلاحية `inventory.adjustment.create`. الجسم `SerializedUnitStockInRequest`. يُرجِع `201`.
- `POST /invTrans/TransferSerializedUnits` — نقل وحدات مُسلسَلة بين الفروع. الصلاحية `inventory.adjustment.create` (بنطاق الفرع المصدر). الجسم `SerializedUnitTransferRequest`.
- `GET /invTrans/SerializedScan/{companyId}/{branchId}/{scanCode}` — البحث عن وحدة مُسلسَلة عبر مسح IMEI/serial/باركود. الصلاحية `inventory.item.read`.
- `GET /invTrans/SerializedUnits/{companyId}/{branchId}/{productId}?status=` — سرد الوحدات المُسلسَلة لمنتج، مع فلترة اختيارية بـ `ProductUnitStatus`. الصلاحية `inventory.item.read`.
- `PUT /invTrans/SerializedUnits/{companyId}/{branchId}/{productId}/{productUnitId}/identifier` — تحديث IMEI/serial/الحالة لوحدة. الصلاحية `inventory.item.edit`.
- `GET /invTrans/SerializedAvailability/{companyId}/{branchId}/{productId}` — عدّ الوحدات المُسلسَلة المتاحة. الصلاحية `inventory.item.read`.
- `GET /invTrans/StockMovements/{companyId}/{branchId}/{productId}?limit=50` — سجل حركة المنتج. الصلاحية `inventory.item.read`.
- `GET /invTrans/SerializedUnitMovements/{companyId}/{branchId}/{productUnitId}?limit=50` — سجل حركة وحدة واحدة. الصلاحية `inventory.item.read`.

## واجهة إدارة الورديات والنقد (`/shiftPeriod`)

المسار الأساسي `/shiftPeriod`. يعالجها `ShiftController` ← `ShiftService`. الوردية جلسة نقدية للكاشير؛ تُنسَب المبيعات خلال الجلسة إليها ويُسوّى النقد عند الإغلاق.

نقاط دورة الحياة الحديثة:

- `POST /shiftPeriod/{companyId}/open` — فتح وردية برصيد افتتاحي وكاشير. الصلاحية `pos.shift.create`. الجسم `OpenShiftRequest`. Idempotent: يُرجِع الوردية المفتوحة الحالية إن كانت هناك واحدة نشطة. يُرجِع `201`.
- `GET /shiftPeriod/{companyId}/{branchId}/active` — الوردية النشطة حاليًا، أو `204` إن لم توجد. الصلاحية `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}` — وردية واحدة مُثراة بالطلبات/الإجماليات. الصلاحية `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/{branchId}/shifts` — كل ورديات الفرع. الصلاحية `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}/reconciliation` — بيانات التسوية لسير الإغلاق. الصلاحية `pos.shift.read`.
- `GET /shiftPeriod/{companyId}/shift/{shiftId}/events` — سجل أحداث تدقيق الوردية. الصلاحية `pos.shift.read`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/cash-movement` — تسجيل إدخال/إخراج نقد خلال الوردية. الصلاحية `pos.shift.edit`. الجسم `CashMovementRequest`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/close` — إغلاق مع تسوية نقدية من جهة الخادم. الصلاحية `pos.shift.close`. الجسم `CloseShiftRequest`.
- `POST /shiftPeriod/{companyId}/shift/{shiftId}/force-close` — إغلاق قسري من المدير مع `reason`. الصلاحية `pos.shift.force_close`.

نقاط قديمة مهجورة (محفوظة للتوافق، تجنّبها للعمل الجديد): `POST /{companyId}/{branchId}/startShift`، `POST /{companyId}/{spId}/endShift`، `POST /{companyId}/currentShift`، `POST /{companyId}/ShiftOrdersById`، `GET /{companyId}/{branchId}/branchShifts`.

أنواع الحركة النقدية تشمل `CASH_SALE` (تُسجَّل تلقائيًا للمبيعات النقدية القياسية) إضافةً إلى إدخالات نقد يدوية داخل/خارج. إغلاق الوردية يكتب إجماليات التسوية وأحداث الوردية؛ ويجب الحفاظ على سجل التدقيق.

## المزامنة دون اتصال (POS Offline Sync)

يمكن لعملاء POS العمل دون اتصال ثم مزامنة الطلبات لاحقًا مع الخادم. هذا المسار idempotent ومُدقَّق. الحزمة الخلفية ذات الصلة: `com.example.valueinsoftbackend.pos.offline`.

المفاهيم: `pos_device` طرفية مسجّلة قادرة على العمل دون اتصال؛ `pos_sync_batch` يجمّع الطلبات دون اتصال أثناء الرفع؛ كل طلب دون اتصال يُدرَج في `pos_offline_order_import` ويُتحقَّق منه، مع تسجيل الإخفاقات في `pos_offline_order_error`؛ `pos_idempotency_key` يربط الطلب دون اتصال بطلبه الرسمي على الخادم بحيث لا تُكرَّر عمليات الترحيل عند الإعادة؛ `pos_sync_audit_log` يسجّل أحداث المزامنة؛ `pos_bootstrap_version` يتتبّع إصدار/checksum بيانات التهيئة (الإعدادات، الكتالوج) المدفوعة للأجهزة.

القواعد: ترحيل الطلب دون اتصال يجب أن يفحص `pos_idempotency_key` قبل إدراج طلب رسمي؛ وإعادة المحاولة يجب ألا تُكرّر آثار الطلب أو المخزون أو المالية.

## جداول قاعدة بيانات POS

جداول POS القديمة المشتركة (أسماء مقتبسة بحالة أحرف مختلطة في `public`):

- `public."PosOrder"` — رأس الطلب: `orderId`، `orderTime`، `clientName`، `orderType`، `orderDiscount`، `orderTotal`، `salesUser`.
- `public."PosOrderDetail"` — سطر الطلب: `orderDetailsId`، `itemId`، `itemName`، `quantity`، `price`، `total`، `orderId`.
- `public."PosProduct"` — منتج قديم: `productId`، `productName`، `rPrice`، `lPrice`، `bPrice`، `serial`، `quantity`، `pState`، `branchId`.
- `public."PosShiftPeriod"` — رأس الوردية: `PosSOID`، `ShiftStartTime`، `ShiftEndTime`، `branchId`.
- `public."PosCateJson"` — JSON فئات لكل فرع.
- `public."MainMajor"` — الفئات التجارية الرئيسية.
- `public."InventoryTransactions"` — صفوف حركة المخزون.

مخططات المستأجرين تُسمّى `c_<companyId>` (مثل `c_1095`) وقد تحتوي جداول قديمة بلاحقة الفرع مثل `c_1095."PosOrder_1074"`، `PosOrderDetail_1074`، `PosProduct_1074`، `InventoryTransactions_1074`. معرّف الفرع مُرمَّز في لاحقة اسم الجدول. لا تُنشئ جداول جديدة بلاحقة الفرع للميزات الحديثة؛ فضّل عمود `branch_id`.

جداول الوردية الحديثة (لكل مخطط مستأجر): `shift_event` (أحداث دورة حياة الوردية)، `shift_cash_movement` (إدخال/إخراج النقد والتعديلات).

جداول المخزون الحديثة الداعمة لمنتجات POS: `inventory_product` (الكتالوج)، `inventory_product_unit` (الوحدات المُسلسَلة/IMEI)، `inventory_branch_stock_balance` (رصيد مخزون الفرع)، `inventory_stock_ledger` / `inventory_stock_movement` (سجل التدقيق)، `inventory_legacy_product_mapping` (الجسر من `PosProduct` القديم).

جداول المزامنة دون اتصال (في `public` / المستأجر): `pos_device`، `pos_device_session`، `pos_sync_batch`، `pos_offline_order_import`، `pos_offline_order_error`، `pos_idempotency_key`، `pos_sync_audit_log`، `pos_bootstrap_version`.

قواعد قاعدة البيانات (راجع وثيقة SQL RAG للتفصيل الكامل): كل تغييرات المخطط تمرّ عبر ترحيلات Flyway في `src/main/resources/db/migration`؛ لا تُعدّل ترحيلًا مُطبَّقًا؛ أعمدة النقود الجديدة تستخدم `NUMERIC`؛ تغييرات وقت التشغيل للمستأجر يجب أن تدور على كل مخطط `c_<companyId>`؛ اقتبس المعرّفات القديمة ذات الأحرف المختلطة تمامًا.

## أسئلة شائعة عن POS (سؤال/جواب)

س: كيف أُنشئ عملية بيع من الـ API؟
ج: `POST /Order/v2/pos/{companyId}/orders` بجسم `CreateOrderRequest` والصلاحية `pos.sale.create`. أدرِج `idempotencyKey` (UUID) لتكون إعادة المحاولة آمنة. `201` تعني طلبًا جديدًا؛ `200` تعني أن المفتاح سبق أن رحّل طلبًا.

س: لماذا لم يسجّل بيعٌ نقدًا في الوردية؟
ج: النقد يُسجَّل تلقائيًا فقط للمبيعات النقدية القياسية (`orderType` = `Dirict`/`Direct`/`مباشر`) بـ `orderTotal > 0` أثناء وجود وردية نشطة. الأنواع غير النقدية (آجل/حساب العميل) تتخطّى حركة `CASH_SALE`.

س: كيف يُمنَع تكرار الطلب؟
ج: عبر `idempotencyKey` في الطلب. عند الإعادة يُرجِع الخادم `CreateOrderResult` الأصلي بـ `idempotencyHit = true` ويتخطّى آثار المخزون والنقد والمالية. الطلبات دون اتصال تستخدم `pos_idempotency_key` للضمان نفسه.

س: كيف تعمل أصناف الهواتف/IMEI في POS؟
ج: هي منتجات مُسلسَلة تُتتبَّع في `inventory_product_unit`. استقبلها عبر `POST /invTrans/AddSerializedStockIn`، وامسحها بـ IMEI/serial عبر `GET /invTrans/SerializedScan/...`، والبيع يستهلك وحدة محددة بدل مجرد خصم كمية.

س: كيف أُغلق وردية نقدية؟
ج: `POST /shiftPeriod/{companyId}/shift/{shiftId}/close` بجسم `CloseShiftRequest` والصلاحية `pos.shift.close`. يُسوّي الخادم النقد المعدود مقابل المتوقّع ويكتب إجماليات التسوية وأحداث الوردية. يمكن للمدير الإغلاق القسري `force-close` بـ `pos.shift.force_close`.

س: من أين تأتي قيود محاسبة البيع؟
ج: يُوضَع قيد مالي للبيع في قائمة الانتظار بعد التزام معاملة الطلب (`FinanceOperationalPostingService`)، فلا تُرحَّل القيود إلا عند نجاح البيع، ويجب أن يساوي المدين الدائن.

## كلمات مفتاحية عالية القيمة للـ RAG

POS، نقاط البيع، طلب، بيع، فاتورة، receiptNumber، كاشير، salesUser، orderType، Dirict، Direct، مباشر، idempotencyKey، idempotency hit، CreateOrderRequest، CreateOrderResult، إرجاع، مرتجع، منتج، كتالوج، باركود، مُسلسَل، IMEI، الرقم التسلسلي، product unit، نوع التتبّع، فئة، MainMajor، الباقة التجارية، عرض، ترويج، خصم، ولاء، نقاط، وردية، فتح وردية، إغلاق وردية، تسوية، حركة نقدية، CASH_SALE، إغلاق قسري، رصيد افتتاحي، صنف تالف، تسوية، حركة مخزون، حركة المخزون، رصيد المخزون، مزامنة دون اتصال، pos_device، pos_idempotency_key، دفعة مزامنة، bootstrap، صلاحية، pos.sale.create، pos.shift.close، companyId، branchId، مستأجر، c_1095، قيد مالي، دفتر يومية.
