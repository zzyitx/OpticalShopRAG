# 采购与批次库存 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现直接入库、订单后续验收、分次到货、批次库存和 SKU 汇总库存守恒的采购闭环。

**Architecture:** 新增采购订单/验收聚合与 `StoreInventoryBatch`，由 `StoreBatchInventoryService` 作为唯一库存写入口，同时更新批次库存、SKU 汇总库存和库存流水。现有 `StoreInboundOrder` 接口保留为兼容入口并委托新库存服务，不再维护第二套库存算法。

**Tech Stack:** Java 17、Spring Boot、Spring Data JPA、MySQL 悲观锁、JUnit 5、Mockito、Vue 3、TypeScript、Naive UI。

---

## 文件结构

### 后端新增

- `src/main/java/com/yizhaoqi/smartpai/model/StoreInventoryBatch.java`
- `src/main/java/com/yizhaoqi/smartpai/model/StorePurchaseOrder.java`
- `src/main/java/com/yizhaoqi/smartpai/model/StorePurchaseOrderItem.java`
- `src/main/java/com/yizhaoqi/smartpai/model/StorePurchaseReceipt.java`
- `src/main/java/com/yizhaoqi/smartpai/model/StorePurchaseReceiptItem.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreInventoryBatchRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StorePurchaseOrderRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StorePurchaseReceiptRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreBatchInventoryService.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StorePurchaseService.java`
- `src/main/java/com/yizhaoqi/smartpai/controller/StorePurchaseController.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreBatchInventoryServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StorePurchaseServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/controller/StorePurchaseControllerTest.java`
- `docs/databases/store-batch-inventory-validation.sql`

### 后端修改

- `src/main/java/com/yizhaoqi/smartpai/model/StoreInventoryLedger.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreInventoryStockRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreInventoryService.java`
- `src/main/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializer.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreInventoryServiceTest.java`

### 前端新增

- `frontend/src/service/api/store-purchase.ts`
- `frontend/src/views/store-purchase/index.vue`
- `frontend/src/views/store-purchase/modules/purchase-order-list.vue`
- `frontend/src/views/store-purchase/modules/purchase-order-editor.vue`
- `frontend/src/views/store-purchase/modules/purchase-receipt-editor.vue`
- `frontend/src/views/store-purchase/modules/purchase-detail-drawer.vue`

### 前端修改或生成

- `frontend/src/service/api/index.ts`
- `frontend/src/router/elegant/imports.ts`
- `frontend/src/router/elegant/routes.ts`
- `frontend/src/router/elegant/transform.ts`
- `frontend/src/typings/elegant-router.d.ts`
- `frontend/src/locales/langs/zh-cn.ts`
- `frontend/src/locales/langs/en-us.ts`

## Task 1：建立批次库存的红灯测试

**Files:**

- Create: `src/test/java/com/yizhaoqi/smartpai/service/StoreBatchInventoryServiceTest.java`

- [ ] **Step 1：写库存增加守恒测试**

```java
@Test
void shouldIncreaseBatchAggregateAndLedgerInOneMovement() {
    StoreProduct product = batchProduct("CL-300", 5);
    StoreInventoryStock stock = aggregateStock("CL-300", 10, 5);
    when(productRepository.findBySku("CL-300")).thenReturn(Optional.of(product));
    when(stockRepository.findByProductSkuAndWarehouseCodeForUpdate("CL-300", "DEFAULT"))
            .thenReturn(Optional.of(stock));
    when(batchRepository.findForUpdate("DEFAULT", "CL-300", "B-001|2028-06-30"))
            .thenReturn(Optional.empty());

    service.increase(new StoreBatchInventoryService.IncreaseCommand(
            "DEFAULT", "CL-300", "B-001", LocalDate.of(2026, 6, 1),
            LocalDate.of(2028, 6, 30), 4, "PO-001",
            StoreInventoryLedger.OperationSource.PURCHASE_ORDER, "admin", "直接入库"
    ));

    assertEquals(14, stock.getCurrentQuantity());
    verify(batchRepository).save(argThat(batch -> batch.getCurrentQuantity() == 4));
    verify(ledgerRepository).save(argThat(ledger ->
            ledger.getChangeQuantity() == 4 && "B-001".equals(ledger.getBatchNo())));
}
```

- [ ] **Step 2：写过期和非批次商品测试**

```java
@Test
void shouldUseInternalBatchForNonBatchProduct() {
    StoreProduct product = nonBatchProduct("FRAME-01");
    when(productRepository.findBySku("FRAME-01")).thenReturn(Optional.of(product));
    when(stockRepository.findByProductSkuAndWarehouseCodeForUpdate("FRAME-01", "DEFAULT"))
            .thenReturn(Optional.empty());
    when(batchRepository.findForUpdate("DEFAULT", "FRAME-01", StoreBatchInventoryService.NO_BATCH))
            .thenReturn(Optional.empty());

    service.increase(new StoreBatchInventoryService.IncreaseCommand(
            "DEFAULT", "FRAME-01", null, null, null, 2, "PO-002",
            StoreInventoryLedger.OperationSource.PURCHASE_ORDER, "admin", null
    ));

    verify(batchRepository).save(argThat(batch -> StoreBatchInventoryService.NO_BATCH.equals(batch.getBatchNo())));
}

@Test
void shouldRejectExpiredManagedBatch() {
    StoreProduct product = batchProduct("CL-300", 5);
    when(productRepository.findBySku("CL-300")).thenReturn(Optional.of(product));

    CustomException ex = assertThrows(CustomException.class, () -> service.increase(
            new StoreBatchInventoryService.IncreaseCommand(
                    "DEFAULT", "CL-300", "OLD", null, LocalDate.now().minusDays(1),
                    1, "PO-003", StoreInventoryLedger.OperationSource.PURCHASE_ORDER, "admin", null
            )
    ));
    assertEquals("STORE_BATCH_EXPIRED", ex.getMessage());
}

@Test
void shouldDecreaseSelectedBatchAndAggregateTogether() {
    StoreInventoryBatch batch = inventoryBatch(9L, "CL-300", "B-09", 5);
    StoreInventoryStock stock = aggregateStock("CL-300", 5, 2);
    when(batchRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(batch));
    when(stockRepository.findByProductSkuAndWarehouseCodeForUpdate("CL-300", "DEFAULT"))
            .thenReturn(Optional.of(stock));

    service.decrease(new StoreBatchInventoryService.DecreaseCommand(
            "DEFAULT", "CL-300", 9L, 2, "SB-001",
            StoreInventoryLedger.OperationSource.SALES_BILL, "seller", "销售审核"
    ));

    assertEquals(3, batch.getCurrentQuantity());
    assertEquals(3, stock.getCurrentQuantity());
    verify(ledgerRepository).save(argThat(ledger -> ledger.getChangeQuantity() == -2));
}
```

- [ ] **Step 3：运行测试并确认失败**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreBatchInventoryServiceTest test
```

Expected: 新实体和服务不存在导致失败。

测试类同时加入以下固定构造器，避免测试依赖未定义辅助函数：

```java
private StoreProduct batchProduct(String sku, int safeStock) {
    StoreProduct product = new StoreProduct();
    product.setSku(sku);
    product.setSafeStock(safeStock);
    product.setBatchManaged(true);
    product.setExpirationManaged(true);
    product.setProductionDateRequired(false);
    product.setStatus(StoreProduct.ProductStatus.ENABLED);
    return product;
}

private StoreProduct nonBatchProduct(String sku) {
    StoreProduct product = batchProduct(sku, 0);
    product.setBatchManaged(false);
    product.setExpirationManaged(false);
    return product;
}

private StoreInventoryStock aggregateStock(String sku, int quantity, int safeStock) {
    StoreInventoryStock stock = new StoreInventoryStock();
    stock.setProductSku(sku);
    stock.setWarehouseCode("DEFAULT");
    stock.setCurrentQuantity(quantity);
    stock.setAvailableQuantity(quantity);
    stock.setSafeStock(safeStock);
    stock.setStatus(StoreInventoryStock.StockStatus.NORMAL);
    return stock;
}

private StoreInventoryBatch inventoryBatch(Long id, String sku, String batchNo, int quantity) {
    StoreInventoryBatch batch = new StoreInventoryBatch();
    batch.setId(id);
    batch.setWarehouseCode("DEFAULT");
    batch.setProductSku(sku);
    batch.setBatchNo(batchNo);
    batch.setBatchKey(batchNo + "|2028-12-31");
    batch.setExpirationDate(LocalDate.of(2028, 12, 31));
    batch.setCurrentQuantity(quantity);
    batch.setAvailableQuantity(quantity);
    batch.setStatus(StoreInventoryBatch.BatchStatus.AVAILABLE);
    return batch;
}
```

## Task 2：实现批次库存实体、仓储和库存写服务

**Files:** `StoreInventoryBatch`、仓储、服务、流水实体及测试。

- [ ] **Step 1：实现批次实体字段**

```java
Long id;
String warehouseCode;
String productSku;
String batchKey;       // warehouse + SKU 范围内唯一的技术键
String batchNo;
LocalDate productionDate;
LocalDate expirationDate;
Integer currentQuantity;
Integer availableQuantity;
LocalDateTime lastInboundAt;
LocalDateTime lastOutboundAt;
BatchStatus status; // AVAILABLE、DEPLETED、EXPIRED、DISABLED
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

唯一约束为 `warehouse_code + product_sku + batch_key`；`batchKey` 由服务生成，格式为 `batchNo|expirationDate`，无有效期使用 `NO_EXPIRY`，非批次商品固定为 `NO_BATCH`。这样避免 MySQL 唯一索引遇到空日期时允许重复。所有字段补齐 Java 与数据库中文注释。

- [ ] **Step 2：实现悲观锁仓储**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
       select batch from StoreInventoryBatch batch
       where batch.warehouseCode = :warehouseCode
         and batch.productSku = :productSku
         and batch.batchKey = :batchKey
       """)
Optional<StoreInventoryBatch> findForUpdate(String warehouseCode,
                                             String productSku,
                                             String batchKey);

@Query("""
       select batch from StoreInventoryBatch batch
       where batch.warehouseCode = :warehouseCode
         and batch.productSku = :productSku
         and batch.availableQuantity > 0
         and (batch.expirationDate is null or batch.expirationDate >= :today)
       order by case when batch.expirationDate is null then 1 else 0 end,
                batch.expirationDate asc, batch.lastInboundAt asc, batch.id asc
       """)
List<StoreInventoryBatch> findAvailableForFefo(String warehouseCode,
                                                String productSku,
                                                LocalDate today);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select batch from StoreInventoryBatch batch where batch.id = :id")
Optional<StoreInventoryBatch> findByIdForUpdate(Long id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
       select batch from StoreInventoryBatch batch
       where batch.warehouseCode = :warehouseCode
         and batch.productSku = :productSku
         and batch.availableQuantity > 0
         and (batch.expirationDate is null or batch.expirationDate >= :today)
       order by case when batch.expirationDate is null then 1 else 0 end,
                batch.expirationDate asc, batch.lastInboundAt asc, batch.id asc
       """)
List<StoreInventoryBatch> findAvailableForFefoForUpdate(String warehouseCode,
                                                         String productSku,
                                                         LocalDate today);
```

- [ ] **Step 3：扩展库存流水**

在 `StoreInventoryLedger` 新增 `batchNo`、`productionDate`、`expirationDate`；`OperationSource` 增加 `PURCHASE_ORDER`、`PURCHASE_RECEIPT`。原有来源保持不变。

- [ ] **Step 4：实现唯一写入口**

`StoreBatchInventoryService.increase` 必须按以下顺序执行：校验商品批次配置 → 锁批次 → 锁 SKU 汇总库存 → 保存批次 → 保存汇总库存 → 保存流水。新汇总库存的 `safeStock` 从商品档案读取，不再固定为 0。

公开记录固定为：

```java
public record IncreaseCommand(String warehouseCode, String productSku, String batchNo,
                              LocalDate productionDate, LocalDate expirationDate,
                              int quantity, String businessOrderNo,
                              StoreInventoryLedger.OperationSource source,
                              String operator, String remark) {}
public record MovementResult(String warehouseCode, String productSku, Long batchId,
                             String batchNo, int batchQuantity, int aggregateQuantity) {}
```

同时实现阶段 3 复用的扣减契约：

```java
public record DecreaseCommand(String warehouseCode, String productSku, Long batchId,
                              int quantity, String businessOrderNo,
                              StoreInventoryLedger.OperationSource source,
                              String operator, String remark) {}

public MovementResult decrease(DecreaseCommand command) {
    StoreInventoryBatch batch = batchRepository.findByIdForUpdate(command.batchId())
            .orElseThrow(() -> new CustomException("STORE_BATCH_NOT_FOUND", HttpStatus.NOT_FOUND));
    if (!batch.getWarehouseCode().equals(command.warehouseCode())
            || !batch.getProductSku().equals(command.productSku())) {
        throw new CustomException("STORE_BATCH_PRODUCT_MISMATCH", HttpStatus.CONFLICT);
    }
    if (batch.getExpirationDate() != null && batch.getExpirationDate().isBefore(LocalDate.now())) {
        throw new CustomException("STORE_BATCH_EXPIRED", HttpStatus.CONFLICT);
    }
    if (batch.getAvailableQuantity() < command.quantity()) {
        throw new CustomException("STORE_INVENTORY_NOT_ENOUGH", HttpStatus.CONFLICT);
    }
    return applyDecrease(batch, command);
}
```

`applyDecrease` 与 `increase` 共用汇总库存锁和流水写入辅助方法，不能复制第二套数量计算。

- [ ] **Step 5：运行批次服务测试**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreBatchInventoryServiceTest test
```

Expected: 正向入库、`NO_BATCH`、过期拒绝和数量守恒测试通过。

## Task 3：建立采购订单与验收聚合

**Files:** 四个采购实体和两个仓储。

- [ ] **Step 1：实现采购订单字段与状态**

```java
// StorePurchaseOrder
Long id;
String orderNo;
StoreBusinessPartner supplier;
String supplierNameSnapshot;
Long purchaserUserId;
String purchaserUsernameSnapshot;
StoreWarehouse warehouse;
String warehouseCodeSnapshot;
InboundMode inboundMode;       // DIRECT、RECEIPT
PurchaseStatus status;         // DRAFT、APPROVED、PARTIALLY_RECEIVED、COMPLETED、CANCELLED
String remark;
String createdBy;
String approvedBy;
LocalDateTime approvedAt;
String cancelledBy;
LocalDateTime cancelledAt;
LocalDateTime createdAt;
LocalDateTime updatedAt;
List<StorePurchaseOrderItem> items;

// StorePurchaseOrderItem
Long id;
StorePurchaseOrder order;
String productSku;
String productNameSnapshot;
Integer orderedQuantity;
Integer receivedQuantity;
BigDecimal unitPrice;
BigDecimal totalAmount;
String batchNo;
LocalDate productionDate;
LocalDate expirationDate;
```

- [ ] **Step 2：实现采购验收字段**

```java
// StorePurchaseReceipt
Long id;
String receiptNo;
StorePurchaseOrder purchaseOrder;
ReceiptStatus status;          // DRAFT、CONFIRMED、CANCELLED
String differenceRemark;
String createdBy;
String confirmedBy;
LocalDateTime confirmedAt;
String cancelledBy;
LocalDateTime cancelledAt;
LocalDateTime createdAt;
List<StorePurchaseReceiptItem> items;

// StorePurchaseReceiptItem
Long id;
StorePurchaseReceipt receipt;
StorePurchaseOrderItem purchaseOrderItem;
Integer receivedQuantity;
String batchNo;
LocalDate productionDate;
LocalDate expirationDate;
```

- [ ] **Step 3：实现订单锁仓储**

`StorePurchaseOrderRepository` 和 `StorePurchaseReceiptRepository` 均提供 `findByIdForUpdate` 悲观锁方法；列表查询支持单号、供应商、状态、日期和分页。

- [ ] **Step 4：编译实体映射**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 编译通过，Hibernate 只新增表和列。

## Task 4：实现直接入库和审核幂等

**Files:**

- Create/Modify: `StorePurchaseService.java`
- Test: `StorePurchaseServiceTest.java`

- [ ] **Step 1：写直接入库审核测试**

```java
@Test
void shouldCompleteDirectPurchaseAndIncreaseEachBatchOnce() {
    StorePurchaseOrder order = directDraftOrder();
    when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

    StorePurchaseService.PurchaseOrderView result = service.approve(1L, "buyer");

    assertEquals(StorePurchaseOrder.PurchaseStatus.COMPLETED, result.status());
    verify(batchInventoryService, times(order.getItems().size())).increase(any());
    verify(orderRepository).save(order);
}

@Test
void shouldReturnCompletedDirectOrderWithoutSecondInventoryWrite() {
    StorePurchaseOrder order = directDraftOrder();
    order.setStatus(StorePurchaseOrder.PurchaseStatus.COMPLETED);
    when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

    service.approve(1L, "buyer");

    verifyNoInteractions(batchInventoryService);
}
```

- [ ] **Step 2：实现创建与审核**

公开契约：

```java
PurchaseOrderView create(PurchaseOrderCreateRequest request, String operator);
PurchaseOrderView update(Long id, PurchaseOrderCreateRequest request, String operator);
PurchaseOrderView approve(Long id, String operator);
PurchaseOrderView cancel(Long id, String operator);
List<PurchaseOrderView> list(PurchaseOrderQuery query);
```

请求记录固定为：

```java
public record PurchaseOrderCreateRequest(Long supplierId, Long purchaserUserId, Long warehouseId,
                                         StorePurchaseOrder.InboundMode inboundMode, String remark,
                                         List<PurchaseItemRequest> items) {}
public record PurchaseItemRequest(String productSku, int orderedQuantity, BigDecimal unitPrice,
                                  String batchNo, LocalDate productionDate, LocalDate expirationDate) {}
public record PurchaseReceiptCreateRequest(Long purchaseOrderId, String differenceRemark,
                                           List<PurchaseReceiptItemRequest> items) {}
public record PurchaseReceiptItemRequest(Long purchaseOrderItemId, int receivedQuantity,
                                         String batchNo, LocalDate productionDate,
                                         LocalDate expirationDate) {}
```

`DIRECT` 明细必须在审核前满足商品批次配置；`approve` 对已经处于目标终态的订单直接返回视图，不再次写库存。`RECEIPT` 审核只把状态改为 `APPROVED`。

- [ ] **Step 3：运行直接入库测试**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StorePurchaseServiceTest#shouldCompleteDirectPurchaseAndIncreaseEachBatchOnce+shouldReturnCompletedDirectOrderWithoutSecondInventoryWrite test
```

Expected: 通过。

## Task 5：实现分次验收和超量阻断

**Files:** `StorePurchaseService.java`、`StorePurchaseServiceTest.java`。

- [ ] **Step 1：写部分验收与超量测试**

```java
@Test
void shouldMoveOrderFromApprovedToPartiallyReceivedThenCompleted() {
    StorePurchaseOrder order = receiptApprovedOrder(10);
    when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    StorePurchaseReceipt first = receiptDraft(101L, order, 4, "B-01");
    StorePurchaseReceipt second = receiptDraft(102L, order, 6, "B-02");
    when(receiptRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(first));
    when(receiptRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(second));

    service.confirmReceipt(101L, "buyer");
    assertEquals(StorePurchaseOrder.PurchaseStatus.PARTIALLY_RECEIVED, order.getStatus());
    assertEquals(4, order.getItems().get(0).getReceivedQuantity());

    service.confirmReceipt(102L, "buyer");
    assertEquals(StorePurchaseOrder.PurchaseStatus.COMPLETED, order.getStatus());
    assertEquals(10, order.getItems().get(0).getReceivedQuantity());
}

@Test
void shouldRejectReceiptAboveRemainingQuantityBeforeInventoryWrite() {
    StorePurchaseOrder order = receiptApprovedOrder(10);
    order.getItems().get(0).setReceivedQuantity(8);
    when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    StorePurchaseReceipt receipt = receiptDraft(103L, order, 3, "B-03");
    when(receiptRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(receipt));

    CustomException ex = assertThrows(CustomException.class,
            () -> service.confirmReceipt(103L, "buyer"));

    assertEquals("STORE_PURCHASE_RECEIPT_EXCEEDS_REMAINING", ex.getMessage());
    verifyNoInteractions(batchInventoryService);
}
```

测试类加入以下采购固定构造器：

```java
private StorePurchaseOrder directDraftOrder() {
    StorePurchaseOrder order = receiptApprovedOrder(2);
    order.setInboundMode(StorePurchaseOrder.InboundMode.DIRECT);
    order.setStatus(StorePurchaseOrder.PurchaseStatus.DRAFT);
    order.getItems().get(0).setBatchNo("B-DIRECT");
    order.getItems().get(0).setExpirationDate(LocalDate.now().plusYears(1));
    return order;
}

private StorePurchaseOrder receiptApprovedOrder(int orderedQuantity) {
    StorePurchaseOrder order = new StorePurchaseOrder();
    order.setId(1L);
    order.setOrderNo("PO-001");
    order.setWarehouseCodeSnapshot("DEFAULT");
    order.setInboundMode(StorePurchaseOrder.InboundMode.RECEIPT);
    order.setStatus(StorePurchaseOrder.PurchaseStatus.APPROVED);
    StorePurchaseOrderItem item = new StorePurchaseOrderItem();
    item.setId(11L);
    item.setOrder(order);
    item.setProductSku("CL-300");
    item.setOrderedQuantity(orderedQuantity);
    item.setReceivedQuantity(0);
    order.getItems().add(item);
    return order;
}

private StorePurchaseReceipt receiptDraft(Long id, StorePurchaseOrder order, int quantity, String batchNo) {
    StorePurchaseReceipt receipt = new StorePurchaseReceipt();
    receipt.setId(id);
    receipt.setReceiptNo("PR-" + id);
    receipt.setPurchaseOrder(order);
    receipt.setStatus(StorePurchaseReceipt.ReceiptStatus.DRAFT);
    StorePurchaseReceiptItem item = new StorePurchaseReceiptItem();
    item.setReceipt(receipt);
    item.setPurchaseOrderItem(order.getItems().get(0));
    item.setReceivedQuantity(quantity);
    item.setBatchNo(batchNo);
    item.setExpirationDate(LocalDate.now().plusYears(1));
    receipt.getItems().add(item);
    return receipt;
}
```

- [ ] **Step 2：实现验收事务**

```java
PurchaseReceiptView createReceipt(PurchaseReceiptCreateRequest request, String operator);
PurchaseReceiptView confirmReceipt(Long receiptId, String operator);
PurchaseReceiptView cancelReceipt(Long receiptId, String operator);
List<PurchaseReceiptView> listReceipts(PurchaseReceiptQuery query);
```

确认时锁验收单和采购订单，逐行计算 `remaining = orderedQuantity - receivedQuantity`，先校验全部明细，再调用批次库存服务；任何一行失败整张验收回滚。已经确认的验收再次确认时返回原视图，不重复入库。

- [ ] **Step 3：运行采购服务全测试**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StorePurchaseServiceTest test
```

Expected: 直接入库、待到货、部分验收、全部完成、超量和重复确认均通过。

## Task 6：接入 Controller、权限和旧入库兼容

**Files:** `StorePurchaseController`、权限目录、`StoreInventoryService` 及测试。

- [ ] **Step 1：登记采购权限**

```java
seed("store.purchase.view", "store", "采购管理", "view", "查看采购订单和验收记录"),
seed("store.purchase.create", "store", "采购管理", "create", "创建和编辑采购订单"),
seed("store.purchase.approve", "store", "采购管理", "approve", "审核采购订单"),
seed("store.purchase.receive", "store", "采购管理", "receive", "创建并确认采购验收"),
```

- [ ] **Step 2：实现 REST 路径**

```text
GET/POST/PUT /api/v1/store/purchases
GET           /api/v1/store/purchases/{id}
POST          /api/v1/store/purchases/{id}/approve
POST          /api/v1/store/purchases/{id}/cancel
GET/POST      /api/v1/store/purchases/receipts
POST          /api/v1/store/purchases/receipts/{id}/confirm
POST          /api/v1/store/purchases/receipts/{id}/cancel
```

- [ ] **Step 3：让旧入库确认委托批次库存服务**

`StoreInventoryService.confirmInbound` 保留旧接口和状态，但每条明细改为调用 `StoreBatchInventoryService.increase`，使用实体已有 `batchNo`、`productionDate`、`expirationDate`；删除旧私有 `increaseStock` 的重复算法。旧创建请求补齐三个批次字段，确保兼容入口也满足同一规则。

- [ ] **Step 4：运行 Controller 与兼容回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StorePurchaseControllerTest,StoreInventoryServiceTest,StoreBatchInventoryServiceTest test
```

Expected: 新采购接口和旧入库接口均通过，库存写算法只有一套。

## Task 7：实现采购前端

**Files:** 前端采购文件及生成路由文件。

- [ ] **Step 1：定义采购 API 契约**

```ts
export type PurchaseInboundMode = 'DIRECT' | 'RECEIPT';
export type PurchaseStatus = 'DRAFT' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CANCELLED';
export interface PurchaseItemRequest {
  productSku: string;
  orderedQuantity: number;
  unitPrice: number;
  batchNo: string | null;
  productionDate: string | null;
  expirationDate: string | null;
}
export interface PurchaseOrderRequest {
  supplierId: number;
  purchaserUserId: number;
  warehouseId: number;
  inboundMode: PurchaseInboundMode;
  remark: string;
  items: PurchaseItemRequest[];
}
```

验收请求包含 `purchaseOrderId`、订单明细 ID、本次实收数量和批次日期。

- [ ] **Step 2：实现订单编辑和验收页面**

直接入库模式显示批次字段；后续验收模式在订单阶段隐藏批次字段。验收页面逐行显示订购、累计已收、本次实收和未收。审核/验收按钮使用 `NPopconfirm` 显示库存影响摘要，失败时保留表单。

- [ ] **Step 3：生成路由与翻译**

```powershell
Set-Location frontend
pnpm.CMD gen-route
```

添加 `store-purchase: '采购管理'` 和英文 `Purchase`。

- [ ] **Step 4：运行前端验证**

```powershell
Set-Location frontend
pnpm.CMD typecheck
pnpm.CMD exec eslint src/service/api/store-purchase.ts src/views/store-purchase --max-warnings=0
```

Expected: typecheck 通过；定向 ESLint 无代码错误。

## Task 8：阶段 2 数量守恒验证与提交

- [ ] **Step 1：写并执行核验 SQL**

`store-batch-inventory-validation.sql` 必须返回：

```sql
SELECT s.product_sku, s.warehouse_code, s.current_quantity,
       COALESCE(SUM(b.current_quantity), 0) AS batch_quantity
FROM store_inventory_stock s
LEFT JOIN store_inventory_batch b
  ON b.product_sku = s.product_sku AND b.warehouse_code = s.warehouse_code
GROUP BY s.id
HAVING s.current_quantity <> COALESCE(SUM(b.current_quantity), 0);
```

Expected: 0 行。

- [ ] **Step 2：运行阶段回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreBatchInventoryServiceTest,StorePurchaseServiceTest,StorePurchaseControllerTest,StoreInventoryServiceTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
Set-Location frontend
pnpm.CMD typecheck
```

Expected: 全部通过。

- [ ] **Step 3：提交阶段 2**

只暂存本计划文件，检查 `git diff --cached --check` 后提交：

```powershell
git commit -m "feat(store): 完成采购验收与批次库存" -m "验证:`n- 采购与批次库存定向测试`n- 后端编译`n- 前端 typecheck`n- 批次与汇总库存守恒 SQL"
```
