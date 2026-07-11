# 销售、经营台与 AI 查询 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现销售草稿/审核、FEFO 批次推荐与改选、批次库存扣减、经营台待办风险和新增只读 AI 查询。

**Architecture:** 沿用并扩展 `StoreSalesBill` 作为销售订单和客户配镜历史主表，新增批次分配明细。`StoreSalesAllocationService` 只负责生成/校验 FEFO 方案，实际扣减统一委托阶段 2 的 `StoreBatchInventoryService`；经营台和 AI 只读服务查询相同的 MySQL 权威数据。

**Tech Stack:** Java 17、Spring Boot、Spring Data JPA、MySQL 悲观锁、JUnit 5、Mockito、Vue 3、TypeScript、Naive UI、现有 AgentToolRegistry/RAG。

---

## 文件结构

### 后端新增

- `src/main/java/com/yizhaoqi/smartpai/model/StoreSalesBatchAllocation.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreSalesBatchAllocationRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreSalesAllocationService.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreSalesAllocationServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/controller/StoreSalesBillWorkflowControllerTest.java`

### 后端修改

- `src/main/java/com/yizhaoqi/smartpai/model/StoreSalesBill.java`
- `src/main/java/com/yizhaoqi/smartpai/model/StoreSalesBillItem.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreSalesBillRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreSalesBillService.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreSalesBillCsvService.java`
- `src/main/java/com/yizhaoqi/smartpai/controller/StoreSalesBillController.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreDashboardService.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java`
- `src/main/java/com/yizhaoqi/smartpai/service/AgentToolRegistry.java`
- `src/main/java/com/yizhaoqi/smartpai/service/LlmProviderRouter.java`
- `src/main/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializer.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreSalesBillServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreSalesBillCsvServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreDashboardServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreQueryServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/AgentToolRegistryTest.java`

### 前端新增

- `frontend/src/views/store-sales/index.vue`
- `frontend/src/views/store-sales/modules/sales-order-list.vue`
- `frontend/src/views/store-sales/modules/sales-order-editor.vue`
- `frontend/src/views/store-sales/modules/batch-allocation-panel.vue`
- `frontend/src/views/store-sales/modules/customer-quick-create.vue`
- `frontend/src/views/store-inventory/index.vue`
- `frontend/src/views/store-inventory/modules/stock-table.vue`
- `frontend/src/views/store-inventory/modules/batch-table.vue`
- `frontend/src/views/store-inventory/modules/ledger-table.vue`

### 前端修改或生成

- `frontend/src/service/api/store.ts`
- `frontend/src/views/store/index.vue`
- Elegant Router 生成文件与中英文路由文案。

## Task 1：锁定 FEFO 分配规则

**Files:**

- Create: `src/test/java/com/yizhaoqi/smartpai/service/StoreSalesAllocationServiceTest.java`

- [ ] **Step 1：写跨批次 FEFO 测试**

```java
@Test
void shouldAllocateNearestExpiryThenOldestInbound() {
    when(batchRepository.findAvailableForFefo("DEFAULT", "CL-300", LocalDate.of(2026, 7, 11)))
            .thenReturn(List.of(
                    batch(1L, "B-EARLY", LocalDate.of(2026, 8, 1), 2),
                    batch(2L, "B-LATE", LocalDate.of(2027, 1, 1), 5)
            ));

    StoreSalesAllocationService.AllocationPlan plan = service.plan(
            "DEFAULT", List.of(new StoreSalesAllocationService.SalesLine(10L, "CL-300", 4)),
            LocalDate.of(2026, 7, 11)
    );

    assertEquals(List.of(
            new StoreSalesAllocationService.BatchAllocation(10L, 1L, "B-EARLY", 2, LocalDate.of(2026, 8, 1)),
            new StoreSalesAllocationService.BatchAllocation(10L, 2L, "B-LATE", 2, LocalDate.of(2027, 1, 1))
    ), plan.allocations());
}
```

- [ ] **Step 2：写库存不足与手工改选测试**

```java
@Test
void shouldRejectWhenAvailableBatchesCannotCoverQuantity() {
    when(batchRepository.findAvailableForFefo("DEFAULT", "CL-300", TODAY))
            .thenReturn(List.of(batch(1L, "B-01", TODAY.plusMonths(2), 1)));

    CustomException ex = assertThrows(CustomException.class,
            () -> service.plan("DEFAULT", List.of(new StoreSalesAllocationService.SalesLine(10L, "CL-300", 2)), TODAY));

    assertEquals("STORE_INVENTORY_NOT_ENOUGH", ex.getMessage());
}

@Test
void shouldAcceptManualAllocationOnlyWhenItMatchesSalesQuantity() {
    List<StoreSalesAllocationService.ManualAllocation> manual = List.of(
            new StoreSalesAllocationService.ManualAllocation(10L, 2L, 3)
    );
    when(batchRepository.findById(2L)).thenReturn(Optional.of(batch(2L, "B-02", TODAY.plusMonths(6), 3)));

    StoreSalesAllocationService.AllocationPlan plan = service.validateManual(
            "DEFAULT", List.of(new StoreSalesAllocationService.SalesLine(10L, "CL-300", 3)), manual, TODAY
    );

    assertEquals(3, plan.allocations().get(0).quantity());
}
```

测试类固定使用以下日期和批次构造器：

```java
private static final LocalDate TODAY = LocalDate.of(2026, 7, 11);

private StoreInventoryBatch batch(Long id, String batchNo, LocalDate expirationDate, int quantity) {
    StoreInventoryBatch batch = new StoreInventoryBatch();
    batch.setId(id);
    batch.setWarehouseCode("DEFAULT");
    batch.setProductSku("CL-300");
    batch.setBatchNo(batchNo);
    batch.setExpirationDate(expirationDate);
    batch.setCurrentQuantity(quantity);
    batch.setAvailableQuantity(quantity);
    batch.setStatus(StoreInventoryBatch.BatchStatus.AVAILABLE);
    return batch;
}
```

- [ ] **Step 3：运行测试并确认失败**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreSalesAllocationServiceTest test
```

Expected: 分配服务不存在导致失败。

## Task 2：实现销售状态和批次分配实体

**Files:** `StoreSalesBill`、`StoreSalesBillItem`、新分配实体与仓储。

- [ ] **Step 1：扩展销售账单**

在 `StoreSalesBill` 新增：

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private StoreBusinessPartner customer;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "warehouse_id")
private StoreWarehouse warehouse;

@Enumerated(EnumType.STRING)
private SalesStatus status; // DRAFT、CONFIRMED、CANCELLED

@Enumerated(EnumType.STRING)
private InventoryEffect inventoryEffect; // NONE、APPLIED

private String confirmedBy;
private LocalDateTime confirmedAt;
private String cancelledBy;
private LocalDateTime cancelledAt;
```

人工新建草稿使用 `DRAFT + NONE`；审核成功使用 `CONFIRMED + APPLIED`；历史 Excel/CSV 导入使用 `CONFIRMED + NONE`，避免导入旧账单扣减当前库存。

- [ ] **Step 2：实现分配实体**

```java
Long id;
StoreSalesBill bill;
StoreSalesBillItem billItem;
StoreInventoryBatch inventoryBatch;
String batchNoSnapshot;
LocalDate expirationDateSnapshot;
Integer quantity;
```

分配记录只在销售审核事务中写入，审核前预览不落库。

- [ ] **Step 3：实现账单锁仓储**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select bill from StoreSalesBill bill where bill.id = :id")
Optional<StoreSalesBill> findByIdForUpdate(Long id);
```

- [ ] **Step 4：编译实体映射**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 编译通过，只新增字段和表。

## Task 3：实现 FEFO 预览和手工分配校验

**Files:** `StoreSalesAllocationService.java`、测试。

- [ ] **Step 1：实现服务记录和入口**

```java
public record SalesLine(Long billItemId, String productSku, int quantity) {}
public record ManualAllocation(Long billItemId, Long batchId, int quantity) {}
public record BatchAllocation(Long billItemId, Long batchId, String batchNo, int quantity,
                              LocalDate expirationDate) {}
public record AllocationPlan(List<BatchAllocation> allocations) {}

public AllocationPlan plan(String warehouseCode, List<SalesLine> lines, LocalDate today) {
    List<BatchAllocation> result = new ArrayList<>();
    for (SalesLine line : lines) {
        int remaining = line.quantity();
        for (StoreInventoryBatch batch : batchRepository.findAvailableForFefo(
                warehouseCode, line.productSku(), today)) {
            int allocated = Math.min(remaining, batch.getAvailableQuantity());
            if (allocated > 0) {
                result.add(new BatchAllocation(line.billItemId(), batch.getId(), batch.getBatchNo(),
                        allocated, batch.getExpirationDate()));
                remaining -= allocated;
            }
            if (remaining == 0) break;
        }
        if (remaining > 0) {
            throw new CustomException("STORE_INVENTORY_NOT_ENOUGH", HttpStatus.CONFLICT);
        }
    }
    return new AllocationPlan(List.copyOf(result));
}

public AllocationPlan validateManual(String warehouseCode, List<SalesLine> lines,
                                     List<ManualAllocation> manual, LocalDate today) {
    Map<Long, SalesLine> lineById = lines.stream()
            .collect(Collectors.toMap(SalesLine::billItemId, Function.identity()));
    Map<Long, Integer> allocatedByLine = new HashMap<>();
    Map<Long, Integer> allocatedByBatch = new HashMap<>();
    List<BatchAllocation> result = new ArrayList<>();
    for (ManualAllocation choice : manual) {
        SalesLine line = Optional.ofNullable(lineById.get(choice.billItemId()))
                .orElseThrow(() -> new CustomException("STORE_SALES_LINE_NOT_FOUND", HttpStatus.BAD_REQUEST));
        StoreInventoryBatch batch = batchRepository.findById(choice.batchId())
                .orElseThrow(() -> new CustomException("STORE_BATCH_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (!warehouseCode.equals(batch.getWarehouseCode()) || !line.productSku().equals(batch.getProductSku())) {
            throw new CustomException("STORE_BATCH_PRODUCT_MISMATCH", HttpStatus.CONFLICT);
        }
        if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(today)) {
            throw new CustomException("STORE_BATCH_EXPIRED", HttpStatus.CONFLICT);
        }
        int batchTotal = allocatedByBatch.merge(choice.batchId(), choice.quantity(), Integer::sum);
        if (choice.quantity() <= 0 || batchTotal > batch.getAvailableQuantity()) {
            throw new CustomException("STORE_INVENTORY_NOT_ENOUGH", HttpStatus.CONFLICT);
        }
        allocatedByLine.merge(choice.billItemId(), choice.quantity(), Integer::sum);
        result.add(new BatchAllocation(choice.billItemId(), batch.getId(), batch.getBatchNo(),
                choice.quantity(), batch.getExpirationDate()));
    }
    for (SalesLine line : lines) {
        if (allocatedByLine.getOrDefault(line.billItemId(), 0) != line.quantity()) {
            throw new CustomException("STORE_SALES_ALLOCATION_QUANTITY_MISMATCH", HttpStatus.CONFLICT);
        }
    }
    return new AllocationPlan(List.copyOf(result));
}
```

实现时先按销售明细分组，FEFO 只使用仓储返回的未过期可用批次；手工分配必须校验批次属于同一仓库和 SKU、未过期、可用量充足，且每条销售明细的分配合计等于销售数量。

- [ ] **Step 2：运行分配服务测试**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreSalesAllocationServiceTest test
```

Expected: FEFO、跨批次、库存不足和手工分配测试通过。

## Task 4：改造销售草稿、审核和取消

**Files:** `StoreSalesBillService`、Controller、CSV 服务及测试。

- [ ] **Step 1：写审核幂等和事务委托测试**

```java
@Test
void shouldConfirmDraftAndApplyEachAllocationOnce() {
    StoreSalesBill bill = draftBillWithOneItem();
    when(salesBillRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
    when(allocationService.plan(eq("DEFAULT"), anyList(), any(LocalDate.class)))
            .thenReturn(new AllocationPlan(List.of(new BatchAllocation(
                    bill.getItems().get(0).getId(), 8L, "B-01", 2, LocalDate.now().plusMonths(6)
            ))));

    service.confirmBill(1L, new ConfirmBillRequest(List.of()), "seller");

    assertEquals(StoreSalesBill.SalesStatus.CONFIRMED, bill.getStatus());
    assertEquals(StoreSalesBill.InventoryEffect.APPLIED, bill.getInventoryEffect());
    verify(batchInventoryService).decrease(any());
    verify(allocationRepository).saveAll(anyList());
}

@Test
void shouldReturnConfirmedBillWithoutSecondDecrease() {
    StoreSalesBill bill = draftBillWithOneItem();
    bill.setStatus(StoreSalesBill.SalesStatus.CONFIRMED);
    bill.setInventoryEffect(StoreSalesBill.InventoryEffect.APPLIED);
    when(salesBillRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));

    service.confirmBill(1L, new ConfirmBillRequest(List.of()), "seller");

    verifyNoInteractions(batchInventoryService, allocationService);
}
```

测试类加入以下账单构造器：

```java
private StoreSalesBill draftBillWithOneItem() {
    StoreSalesBill bill = new StoreSalesBill();
    bill.setId(1L);
    bill.setBillNo("SB-001");
    bill.setStatus(StoreSalesBill.SalesStatus.DRAFT);
    bill.setInventoryEffect(StoreSalesBill.InventoryEffect.NONE);
    StoreWarehouse warehouse = new StoreWarehouse();
    warehouse.setId(1L);
    warehouse.setCode("DEFAULT");
    bill.setWarehouse(warehouse);
    StoreSalesBillItem item = new StoreSalesBillItem();
    item.setId(11L);
    item.setBill(bill);
    item.setProductSku("CL-300");
    item.setQuantity(2);
    bill.getItems().add(item);
    return bill;
}
```

- [ ] **Step 2：改造公开契约**

```java
SalesBillView createBill(SalesBillCreateRequest request, String operator); // 只保存草稿
AllocationPlanView previewAllocation(AllocationPreviewRequest request);
SalesBillView confirmBill(Long id, ConfirmBillRequest request, String operator);
SalesBillView cancelBill(Long id, String operator);
SalesBillView updateBill(Long id, SalesBillUpdateRequest request, String operator);
```

`createBill` 删除 `autoOutbound` 语义，不再创建即扣库存。`confirmBill` 锁账单，确认仍为草稿后重新校验客户、商品和批次；自动或手工方案都通过分配服务，随后逐笔调用 `StoreBatchInventoryService.decrease`，保存分配记录，再更新状态。重复审核返回原账单。

`SalesBillCreateRequest` 的客户与仓库入口改为：

```java
Long customerId;
Long warehouseId;
```

服务加载启用且具有客户角色的 `StoreBusinessPartner`，把名称和手机号写入账单快照；历史导入允许 `customer` 关联为空，但必须继续保存并校验快照手机号。销售草稿保存成功并获得账单明细 ID 后，再调用分配预览接口，避免使用尚未落库的临时行号。

- [ ] **Step 3：保留历史导入语义**

`StoreSalesBillCsvService` 调用专用 `createHistoricalBill`，设置 `CONFIRMED + NONE`，不进入批次分配和库存扣减。测试断言导入 10 条历史账单不会调用批次库存服务。

- [ ] **Step 4：增加接口和权限**

```text
POST /api/v1/store/sales-bills/allocation-preview
POST /api/v1/store/sales-bills/{id}/confirm
POST /api/v1/store/sales-bills/{id}/cancel
```

权限目录增加：

```java
seed("store.sales-bill.approve", "store", "销售账单", "approve", "审核销售账单并确认出库"),
```

- [ ] **Step 5：运行销售回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreSalesAllocationServiceTest,StoreSalesBillServiceTest,StoreSalesBillCsvServiceTest,StoreSalesBillWorkflowControllerTest test
```

Expected: 草稿不扣库存、审核一次扣减、重复审核幂等、历史导入不扣库存、手机号历史查询保持通过。

## Task 5：扩展经营台和库存读页面

**Files:** Dashboard service/test、库存前端页面、现有经营台。

- [ ] **Step 1：扩展经营台 DTO 与测试**

`StoreDashboardView` 增加：

```java
long pendingPurchaseApprovalCount;
long pendingPurchaseReceiptCount;
long pendingSalesApprovalCount;
long nearExpiryBatchCount;
long expiredBatchCount;
boolean masterDataReady;
```

`masterDataReady` 仅在单位、分类、往来单位、默认仓库和商品均至少有一条可用记录时为 `true`。待办计数使用仓储 count 查询，不加载整表。

- [ ] **Step 2：实现库存批次只读接口**

在库存 Controller 增加：

```text
GET /api/v1/store/inventory/batches?productSku=&status=&nearExpiryOnly=&page=&size=
```

响应包含批号、生产日期、有效期、当前量、可用量、状态和剩余天数；`NO_BATCH` 不显示给页面。

- [ ] **Step 3：拆分经营台和库存页面**

`frontend/src/views/store/index.vue` 只保留指标卡、首次配置引导、待办、风险和快捷入口。新 `store-inventory` 页面使用三个模块分别展示 SKU 汇总、批次库存和流水，保留现有有界查询参数。

- [ ] **Step 4：运行看板与前端类型验证**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreDashboardServiceTest test
Set-Location frontend
pnpm.CMD typecheck
```

Expected: 经营台计数测试和前端 typecheck 通过。

## Task 6：新增采购与批次 AI 只读工具

**Files:** StoreQueryService、AgentToolRegistry、LlmProviderRouter 及测试。

- [ ] **Step 1：写查询服务失败测试**

```java
@Test
void shouldReturnPendingPurchasesWithStructuredSource() {
    when(purchaseOrderRepository.searchOrders(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(pendingPurchase("PO-001")));

    QueryResult<PurchaseOrderQueryView> result = service.queryPurchaseOrders(
            new PurchaseOrderQuery(null, StorePurchaseOrder.PurchaseStatus.APPROVED, 10)
    );

    assertEquals("store_purchase_order", result.source());
    assertEquals(1, result.recordCount());
}

@Test
void shouldReturnNearExpiryBatchFromMysql() {
    QueryResult<BatchInventoryQueryView> result = service.queryBatchInventory(
            new BatchInventoryQuery("CL-300", true, 10)
    );
    assertEquals("store_inventory_batch", result.source());
}
```

查询视图和测试构造器固定为：

```java
public record PurchaseOrderQueryView(String orderNo, String supplierName,
                                     StorePurchaseOrder.InboundMode inboundMode,
                                     StorePurchaseOrder.PurchaseStatus status,
                                     int remainingQuantity) {}

private StorePurchaseOrder pendingPurchase(String orderNo) {
    StorePurchaseOrder order = new StorePurchaseOrder();
    order.setOrderNo(orderNo);
    order.setSupplierNameSnapshot("测试供应商");
    order.setInboundMode(StorePurchaseOrder.InboundMode.RECEIPT);
    order.setStatus(StorePurchaseOrder.PurchaseStatus.APPROVED);
    return order;
}
```

- [ ] **Step 2：实现两个工具定义**

`AgentToolRegistry` 新增 `query_purchase_order` 和 `query_batch_inventory`，沿用现有 `QueryResult` 的 `source`、`records`、`recordCount`、`limit`、`truncated`。工具只读，不注册审核或库存修改函数。

原 `query_sales_bill` 的隐私门槛保持不变：`customerPhone` 与 `billNo` 至少提供一个；只给 `customerName` 时继续返回 `STORE_QUERY_CUSTOMER_PHONE_REQUIRED`。在 `StoreQueryServiceTest` 保留该断言，不能因新增客户档案而放宽。

- [ ] **Step 3：更新路由提示词**

`LlmProviderRouter` 明确：采购状态、批次库存、有效期和数量来自 MySQL 工具；注册证、说明书和资质来自 RAG；AI 不执行审核。

- [ ] **Step 4：运行 AI 定向回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreQueryServiceTest,AgentToolRegistryTest test
```

Expected: 原五个 Store 工具和新增两个工具全部通过，销售账单隐私门槛未放松。

## Task 7：实现销售前端和 FEFO 交互

**Files:** 销售页面、API、路由和本地化文件。

- [ ] **Step 1：更新销售 API 类型**

```ts
export type StoreSalesStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
export interface SalesBatchAllocation {
  billItemId: number;
  batchId: number;
  batchNo: string;
  expirationDate: string | null;
  quantity: number;
}
export interface ConfirmSalesBillRequest {
  allocations: Array<{ billItemId: number; batchId: number; quantity: number }>;
}
```

新增预览、确认和取消 API；创建请求删除 `autoOutbound`。

- [ ] **Step 2：实现销售编辑页面**

客户使用可搜索选择器并支持快速新增；添加商品后调用预览 API。批次分配默认折叠，跨批次、近效期或人工改选时展开。审核前显示商品数量和批次扣减摘要；失败时不清空草稿表单。

- [ ] **Step 3：生成销售和库存路由**

```powershell
Set-Location frontend
pnpm.CMD gen-route
```

添加中文 `销售管理`、`库存管理` 和英文翻译。

- [ ] **Step 4：运行前端验证**

```powershell
Set-Location frontend
pnpm.CMD typecheck
pnpm.CMD exec eslint src/service/api/store.ts src/views/store src/views/store-sales src/views/store-inventory --max-warnings=0
```

Expected: typecheck 通过；定向 ESLint 无代码错误。

## Task 8：阶段 3 完整回归与提交

- [ ] **Step 1：运行 Store 定向测试与编译**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreSalesAllocationServiceTest,StoreSalesBillServiceTest,StoreSalesBillCsvServiceTest,StoreSalesBillWorkflowControllerTest,StoreDashboardServiceTest,StoreQueryServiceTest,AgentToolRegistryTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 通过。

- [ ] **Step 2：运行前端 typecheck 和差异检查**

```powershell
Set-Location frontend
pnpm.CMD typecheck
Set-Location ..
git diff --check
git status --short
```

Expected: typecheck 通过，差异只包含阶段 3 明确文件和用户既有改动。

- [ ] **Step 3：提交阶段 3**

```powershell
git commit -m "feat(store): 完成批次销售与经营助手查询" -m "验证:`n- 销售、看板与 AI 定向测试`n- 后端编译`n- 前端 typecheck"
```
