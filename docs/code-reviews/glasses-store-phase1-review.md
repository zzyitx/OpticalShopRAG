# 代码审查报告：眼镜店改造阶段一（codex-glasses-store-phase1）

> 审查日期：2026-06-08
> 审查分支：`codex-glasses-store-phase1`
> 对比基线：`HEAD` (09a4a8fa)
> 审查人：Hermes Agent
> 关联文档：`docs/glasses-store/implementation/glasses-store-phase1-implementation-draft.md`

---

## 目录

1. [改动概览](#1-改动概览)
2. [严重问题（CRITICAL）— 阻止运行](#2-严重问题critical--阻止运行)
3. [警告问题（WARNING）— 建议修复](#3-警告问题warning--建议修复)
4. [信息提示（INFO）— 值得注意但无害](#4-信息提示info--值得注意但无害)
5. [正面评价 — 做得好的设计](#5-正面评价--做得好的设计)
6. [修改建议汇总](#6-修改建议汇总)
7. [审核通过标准](#7-审核通过标准)

---

## 1. 改动概览

### 1.1 统计数据

| 维度 | 数量 |
|------|------|
| 总变更文件 | 195 个 |
| 纯格式化文件（CRLF→LF） | 186 个 |
| 实质修改文件 | 9 个 |
| 新增后端 Java 文件 | 23 个 |
| 新增前端文件 | 1 个 |
| 新增文档文件 | 4 个 |
| 新增测试文件 | 5 个 |
| 新增代码行数（净增） | ~20 行（等价重格式化） |

### 1.2 变更分类

#### A. 安全配置脱敏（9 个实质修改文件）

| 文件 | 变更说明 |
|------|---------|
| `.env.example` | 所有默认密码、密钥改为 `replace-with-xxx` 占位符 |
| `.gitignore` | 新增 `.codegraph/` 排除项 |
| `application.yml` | 微信支付敏感配置默认值清空 |
| `frontend/src/locales/langs/en-us.ts` | 新增 `store` 路由国际化 |
| `frontend/src/locales/langs/zh-cn.ts` | 新增"眼镜店经营台"路由国际化 |
| `frontend/src/router/elegant/imports.ts` | 新增 store 模块懒加载导入 |
| `frontend/src/router/elegant/routes.ts` | 新增 `/store` 路由定义 |
| `frontend/src/router/elegant/transform.ts` | 新增 store 路径映射 |
| `frontend/src/typings/elegant-router.d.ts` | 新增 store 路由类型声明 |

#### B. 新增眼镜店业务模块（28 个新增文件）

**领域模型层（10 个 Entity）：**

| 文件 | 用途 | 数据库表 |
|------|------|---------|
| `StoreProduct.java` | 商品档案，SKU 为业务主键 | `store_product` |
| `StoreProduct.ProductCategory` | 商品分类枚举（FRAME/LENS/SUNGLASSES/...） | — |
| `StoreProduct.ProductUnit` | 库存单位枚举（PAIR/PIECE/BOX/BOTTLE/ITEM） | — |
| `StoreProduct.ProductStatus` | 商品状态枚举（ENABLED/DISABLED/OUT_OF_STOCK/DELISTED） | — |
| `StoreInventoryStock.java` | 单仓库存现存量 | `store_inventory_stock` |
| `StoreInventoryLedger.java` | 库存流水（每次变化前后数量） | `store_inventory_ledger` |
| `StoreInboundOrder.java` | 入库单主表 | `store_inbound_order` |
| `StoreInboundItem.java` | 入库单明细 | `store_inbound_item` |
| `StoreOutboundOrder.java` | 出库单主表 | `store_outbound_order` |
| `StoreOutboundItem.java` | 出库单明细 | `store_outbound_item` |
| `StoreSalesBill.java` | 销售账单（含验光度数） | `store_sales_bill` |
| `StoreSalesBillItem.java` | 销售账单商品明细快照 | `store_sales_bill_item` |
| `StoreSalesBillChangeLog.java` | 账单修改记录（修改前后快照） | `store_sales_bill_change_log` |

**数据访问层（7 个 Repository）：**

| 文件 | 自定义查询方法 |
|------|---------------|
| `StoreProductRepository` | `findBySku`, `existsBySku` |
| `StoreInventoryStockRepository` | `findByProductSkuAndWarehouseCode`, `countByStatus` |
| `StoreInventoryLedgerRepository` | `findByProductSkuOrderByOperatedAtDesc` |
| `StoreInboundOrderRepository` | `findByOrderNo` |
| `StoreOutboundOrderRepository` | `findByOrderNo` |
| `StoreSalesBillRepository` | `findByBillNo`, `findAllByOrderBy...`, `findByCustomerPhoneOrderBy...`, `countByPurchaseDate`, `sumActualAmountByPurchaseDate` |
| `StoreSalesBillChangeLogRepository` | `findByBillIdOrderByChangedAtDesc` |

**业务逻辑层（5 个 Service）：**

| 文件 | 核心职责 |
|------|---------|
| `StoreProductService` | 商品 CRUD、SKU 唯一性校验、状态管理 |
| `StoreInventoryService` | 入库/出库单创建确认、库存增减、流水写入、库存不足校验 |
| `StoreSalesBillService` | 销售账单 CRUD、客户历史查询、账单修改记录写入 |
| `StoreSalesBillCsvService` | CSV 模板下载、导入解析（含引用转义处理） |
| `StoreDashboardService` | 看板摘要：商品数、库存预警数、今日账单数/金额 |

**控制器层（4 个 Controller）：**

| 文件 | API 路径前缀 |
|------|-------------|
| `StoreProductController` | `/api/v1/store/products` |
| `StoreInventoryController` | `/api/v1/store/inventory` |
| `StoreSalesBillController` | `/api/v1/store/sales-bills` |
| `StoreDashboardController` | `/api/v1/store/dashboard` |

**测试层（5 个 Test）：**

| 文件 | 测试覆盖 |
|------|---------|
| `StoreProductServiceTest` | SKU 唯一性、创建成功路径、重复 SKU 拒绝 |
| `StoreInventoryServiceTest` | 入库草稿创建、入库确认库存增加+流水写入、出库库存不足拒绝 |
| `StoreSalesBillServiceTest` | 同号客户多次开单、历史记录不覆盖旧单、修改写 ChangeLog、缺少手机号拒绝 |
| `StoreSalesBillCsvServiceTest` | CSV 导入 |
| `StoreDashboardServiceTest` | 看板摘要 |

**前端层（1 个页面）：**

| 文件 | 说明 |
|------|------|
| `frontend/src/views/store/index.vue` | 眼镜店经营台：看板指标卡 + 商品档案表 + 库存表 + 销售单表 + 新增商品/销售单表单 + CSV 导入/模板下载 |

---

## 2. 严重问题（CRITICAL）— 阻止运行

### 2.1 前端 Vue 文件缺少必要的 import 语句

**文件：** `frontend/src/views/store/index.vue`
**严重程度：** CRITICAL
**影响范围：** 前端页面无法渲染，浏览器控制台抛 ReferenceError

#### 问题描述

该组件使用了以下 Vue API，但只从 `vue` 导入了 `h`：

```typescript
// 当前导入（第 2 行）：
import { h } from 'vue';

// 实际使用但未导入的 API（第 52-240 行）：
//   ref()         — 第 52 行 loading, dashboard, products, stocks, bills
//   reactive()    — 第 62 行 productForm, 第 70 行 billForm
//   computed()    — 第 92 行 metricCards
//   onMounted()   — 第 240 行
```

同时，`request` 函数（第 149-228 行多处使用）也未导入。该函数定义在 `@/service/request/index.ts` 中，但项目中的其他页面（如 `recharge/index.vue`）使用的是 `@/service/api` 中封装的 `fetchXxx` 方法，而非直接使用 `request`。

#### 现有代码风格参照

项目中 `recharge/index.vue` 的正确导入方式：

```typescript
import { computed, onMounted, ref, h } from 'vue';
import { fetchRechargePackages, fetchCreateRechargeOrder } from '@/service/api';
```

#### 建议修改

**方案一（推荐）：** 遵循项目现有风格，创建 `@/service/api` 中的 store 专用 API 方法

```typescript
// frontend/src/views/store/index.vue — 修复导入
import { computed, onMounted, reactive, ref, h } from 'vue';

// 建议在 @/service/api 中新增 store API 封装后导入
// import { fetchStoreDashboard, fetchStoreProducts, ... } from '@/service/api';
```

如果暂时不重构 API 层，至少要加 `request` 的导入：

```typescript
import { request } from '@/service/request';
```

---

### 2.2 前后端枚举值不一致

**文件：** `frontend/src/views/store/index.vue` vs `model/StoreProduct.java`
**严重程度：** CRITICAL
**影响范围：** 创建商品、选择分类/单位/状态时前后端数据不匹配，接口返回 400 Bad Request 或数据写入失败

#### 不一致详情

##### 2.2.1 商品分类（ProductCategory）

| 前端定义（第 6 行） | 后端定义（StoreProduct.java 142-150 行） | 差异 |
|---------------------|------------------------------------------|------|
| `FRAME` | `FRAME` | ✅ 匹配 |
| `LENS` | `LENS` | ✅ 匹配 |
| `CONTACT_LENS` | `CONTACT_LENS` | ✅ 匹配 |
| **`CARE_PRODUCT`** | **`CARE_SOLUTION`** | ❌ 不匹配 |
| `ACCESSORY` | `ACCESSORY` | ✅ 匹配 |
| `OTHER` | `OTHER` | ✅ 匹配 |
| **缺失** | **`SUNGLASSES`** | ❌ 前端缺失 |

前端传入 `CARE_PRODUCT` 时，后端 Jackson 反序列化枚举失败，接口返回 400 错误。

##### 2.2.2 商品单位（ProductUnit）

| 前端定义（第 7 行） | 后端定义（StoreProduct.java 152-158 行） | 差异 |
|---------------------|------------------------------------------|------|
| `PIECE` | `PIECE` | ✅ 匹配 |
| `PAIR` | `PAIR` | ✅ 匹配 |
| `BOX` | `BOX` | ✅ 匹配 |
| `BOTTLE` | `BOTTLE` | ✅ 匹配 |
| **`SET`** | **`ITEM`** | ❌ 不匹配 |

##### 2.2.3 商品状态（ProductStatus）

| 前端定义（第 8 行） | 后端定义（StoreProduct.java 160-165 行） | 差异 |
|---------------------|------------------------------------------|------|
| `ENABLED` | `ENABLED` | ✅ 匹配 |
| `DISABLED` | `DISABLED` | ✅ 匹配 |
| **缺失** | **`OUT_OF_STOCK`** | ❌ 前端缺失 |
| **缺失** | **`DELISTED`** | ❌ 前端缺失 |

前端缺失这两个状态会导致无法正确展示处于"缺货"和"下架"状态的商品。

##### 2.2.4 库存状态（StockStatus）

| 前端定义（第 35 行） | 后端定义（StoreInventoryStock.java 85-90 行） | 差异 |
|---------------------|------------------------------------------------|------|
| `NORMAL` | `NORMAL` | ✅ 匹配 |
| `LOW_STOCK` | `LOW_STOCK` | ✅ 匹配 |
| `OUT_OF_STOCK` | `OUT_OF_STOCK` | ✅ 匹配 |
| **缺失** | **`DISABLED`** | ❌ 前端缺失 |

虽然渲染函数 `renderStockStatus`（第 231 行）只处理了三种状态且有兜底，但类型定义不完整。

#### 建议修改

**统一方案：** 前端类型定义必须与后端枚举严格对齐。

```typescript
// 修复后的前端类型定义
type ProductCategory = 'FRAME' | 'LENS' | 'SUNGLASSES' | 'CONTACT_LENS' | 'CARE_SOLUTION' | 'ACCESSORY' | 'OTHER';
type ProductUnit = 'PAIR' | 'PIECE' | 'BOX' | 'BOTTLE' | 'ITEM';
type ProductStatus = 'ENABLED' | 'DISABLED' | 'OUT_OF_STOCK' | 'DELISTED';
type StockStatus = 'NORMAL' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'DISABLED';
```

对应的前端 Option 列表也要同步更新：

```typescript
const categoryOptions = [
  { label: '镜架', value: 'FRAME' },
  { label: '镜片', value: 'LENS' },
  { label: '太阳镜', value: 'SUNGLASSES' },      // 新增
  { label: '隐形眼镜', value: 'CONTACT_LENS' },
  { label: '护理液', value: 'CARE_SOLUTION' },    // 修正：CARE_PRODUCT → CARE_SOLUTION
  { label: '配件', value: 'ACCESSORY' },
  { label: '其他', value: 'OTHER' }
];

const unitOptions = [
  { label: '副', value: 'PAIR' },
  { label: '片', value: 'PIECE' },
  { label: '盒', value: 'BOX' },
  { label: '瓶', value: 'BOTTLE' },
  { label: '个', value: 'ITEM' }                  // 修正：SET → ITEM
];
```

---

## 3. 警告问题（WARNING）— 建议修复

### 3.1 Controller 操作人解析是占位实现

**文件：** `StoreProductController.java`, `StoreInventoryController.java`, `StoreSalesBillController.java`, `StoreDashboardController.java`
**严重程度：** WARNING
**影响范围：** 审计字段（createdBy、updatedBy、confirmedBy）无法追溯到真实用户

#### 问题描述

所有 4 个 Store Controller 的 `resolveOperator()` 方法实现相同：

```java
private String resolveOperator(String authorization) {
    if (authorization == null || authorization.isBlank()) {
        return "system";
    }
    return "authenticated-user";  // 硬编码，非真实用户
}
```

这导致：
- 所有已登录用户的操作都被记录为 `"authenticated-user"`
- `createdBy`、`updatedBy`、`confirmedBy` 等审计字段无法区分操作人
- 审计日志和修改追溯失去业务价值

#### 建议修改

项目中已存在 JWT 认证过滤器 `JwtAuthenticationFilter.java`，应从中提取用户信息：

```java
// 方案一：从 SecurityContext 获取当前用户
private String resolveOperator() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
        return authentication.getName(); // 返回用户名
    }
    return "system";
}

// 不再需要从 Controller 方法参数中传入 authorization header
```

如果暂时无法接入 SecurityContext（例如接口在未登录白名单中），建议至少在代码注释中标注 `TODO` 说明这是阶段一临时实现：

```java
/**
 * TODO(phase2): 接入 JWT Authentication 获取真实用户标识。
 * 当前为阶段一占位实现，所有已登录用户统一记录为 "authenticated-user"。
 */
private String resolveOperator(String authorization) {
    // ...
}
```

---

### 3.2 缺少新表 DDL 建表语句

**文件：** `docs/databases/ddl.sql`
**严重程度：** WARNING
**影响范围：** 生产环境无法通过 DDL 脚本建表，依赖 Hibernate `ddl-auto`

#### 问题描述

新增了 9 张 store 业务表，但 `docs/databases/ddl.sql` 未同步更新。在 `application.yml` 中如果 `ddl-auto` 设置为 `none` 或 `validate`（生产环境推荐配置），应用启动会因找不到表而失败。

#### 建议修改

在 `docs/databases/ddl.sql` 中补全以下 9 张表的 DDL（表名和字段定义参考各 Entity 的 `@Table` 和 `@Column` 注解）：

- `store_product`
- `store_inventory_stock`
- `store_inventory_ledger`
- `store_inbound_order`
- `store_inbound_item`
- `store_outbound_order`
- `store_outbound_item`
- `store_sales_bill`
- `store_sales_bill_item`
- `store_sales_bill_change_log`

示例（store_product 表）：

```sql
CREATE TABLE IF NOT EXISTS store_product (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品档案自增主键',
    sku         VARCHAR(64)  NOT NULL COMMENT '商品唯一SKU',
    name        VARCHAR(128) NOT NULL COMMENT '商品名称',
    category    VARCHAR(32)  NOT NULL COMMENT '商品分类',
    brand       VARCHAR(128) COMMENT '商品品牌',
    -- ... 其余字段参照 StoreProduct.java
    CONSTRAINT uk_store_product_sku UNIQUE (sku),
    INDEX idx_store_product_category (category),
    INDEX idx_store_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='眼镜店商品档案表';
```

---

### 3.3 前端未遵循项目 API 请求模式

**文件：** `frontend/src/views/store/index.vue`
**严重程度：** WARNING
**影响范围：** 代码风格不一致，后期维护困难

#### 问题描述

项目中其他页面（如 `recharge/index.vue`）使用 `@/service/api` 中预封装的 `fetchXxx` 方法，而非直接使用 `request` 函数。新页面打破了这一约定。

```typescript
// 现有模式（recharge/index.vue）：
import { fetchRechargePackages, fetchCreateRechargeOrder } from '@/service/api';
const { error, data } = await fetchRechargePackages();

// 新页面模式（store/index.vue）：
const { error, data } = await request<StoreDashboardView>({ url: '/store/dashboard/summary' });
```

#### 建议修改

在 `@/service/api` 中新增 store 模块的 API 封装函数：

```typescript
// frontend/src/service/api/store.ts
import { request } from '../request';

export function fetchStoreDashboard() {
  return request<StoreDashboardView>({ url: '/store/dashboard/summary' });
}

export function fetchStoreProducts() {
  return request<StoreProductView[]>({ url: '/store/products' });
}

// ... 其他 API
```

然后在组件中导入使用，保持与项目其他页面一致的风格。

---

## 4. 信息提示（INFO）— 值得注意但无害

### 4.1 订单号生成使用 System.nanoTime()

**文件：** `StoreInventoryService.java` 第 254-256 行
**严重程度：** INFO
**影响范围：** 极高并发下可能有碰撞风险

#### 描述

```java
private String generateOrderNo(String prefix) {
    return prefix + "-" + LocalDate.now().format(ORDER_DATE_FORMATTER) + "-" + System.nanoTime();
}
```

`System.nanoTime()` 在同一毫秒内、同一个 JVM 进程中可能返回相同值。虽然不会回绕到负值，但在非常偶然的情况下（同一纳秒内两次调用）可能产生重复订单号。

#### 建议

对于单店场景，此风险极低。如需更安全的方式，可用：

```java
private static final AtomicLong ORDER_SEQ = new AtomicLong(0);

private String generateOrderNo(String prefix) {
    return prefix + "-" + LocalDate.now().format(ORDER_DATE_FORMATTER) + "-" + ORDER_SEQ.incrementAndGet();
}
```

### 4.2 CRLF→LF 格式化未使用 .gitattributes 重归一化

**文件：** 186 个文件的 diff 全部是换行符变更
**严重程度：** INFO
**影响范围：** 无功能影响，但 commit diff 过于庞大

#### 描述

`.gitattributes` 已配置 LF 规则，但 186 个已有文件的 CRLF→LF 转换与业务改动混在了同一个分支。建议将来将纯格式化变更独立为一个 commit。

---

## 5. 正面评价 — 做得好的设计

### 5.1 安全配置脱敏

`.env.example` 和 `application.yml` 中的敏感值全部替换为占位符，执行标准严格：

- 所有密码 → `replace-with-xxx`
- JWT Secret → 去掉真实 Base64 值
- 微信支付密钥 → 默认值清空
- MinIO/Elasticsearch 凭证 → 替换为占位符

这是阶段一实施计划中明确要求的安全改造，执行到位。

### 5.2 实体设计规范

所有 JPA 实体有以下特点：

- **字段注释完整**：每个 `@Column` 都配有 `@Comment`，说明业务含义、单位和约束
- **索引设计合理**：高频查询字段（SKU、分类、状态、手机号、购买日期）均有索引
- **唯一约束正确**：SKU 唯一、SKU+仓库联合唯一、账单号唯一
- **审计字段齐全**：`createdBy`、`updatedBy`、`createdAt`、`updatedAt` 覆盖所有核心实体

### 5.3 事务一致性保护

入库/出库确认流程是库存模块最关键的代码路径，设计严谨：

```java
@Transactional
public InboundOrderView confirmInbound(Long orderId, String operator) {
    // 1. 校验单据状态必须是 DRAFT
    // 2. 遍历明细，逐行更新现货存量
    // 3. 逐行写入库存流水
    // 4. 修改单据状态为 CONFIRMED
    // — 以上四步在同一事务内，任何一步失败全部回滚
}
```

出库确认时，库存不足会抛出 `CustomException`，阻止流水写入和状态变更——事务回滚保证数据一致性。

### 5.4 账单审计日志

`StoreSalesBillChangeLog` 记录了每次修改的前后快照：

```java
// 修改前写入 old snapshot
String before = snapshotBill(bill);
applyFields(bill, request, operator);
// 修改后写入 new snapshot
changeLog.setBeforeSnapshot(before);
changeLog.setAfterSnapshot(snapshotBill(saved));
```

这种方式可以追溯到任何关键字段（客户、度数、金额）的修改历史，满足审计要求。

### 5.5 测试覆盖合理

- 测试了核心业务路径：创建、确认、拒绝
- 测试了边界条件：重复 SKU、库存不足、缺手机号
- 测试了关键副作用：ChangeLog 写入、库存流水写入
- 测试了并发场景：同一客户多次开单不冲突

### 5.6 模块隔离良好

新增的眼镜店模块完全独立于原有的 RAG 知识库系统：
- 没有修改任何已有 Controller/Service 的业务逻辑
- 没有破坏原有 API 路径
- 没有修改原有数据库表结构

### 5.7 CSV 导入实现健壮

`StoreSalesBillCsvService.parseCsvLine()` 正确实现了带引号转义的 CSV 解析：
- 支持双引号转义 `""` → `"`
- 正确处理逗号在引号内的情况
- 按行报告错误，成功的记录正常入库

---

## 6. 修改建议汇总

### 优先修改（CRITICAL — 不改无法运行）

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| 1 | 缺少 Vue import | `frontend/src/views/store/index.vue` | 补充 `ref`, `reactive`, `computed`, `onMounted` 导入；补充 `request` 导入或使用 `@/service/api` 封装 | 5 分钟 |
| 2 | 前后端枚举不一致 | `frontend/src/views/store/index.vue` | 修正 `ProductCategory` 的 `CARE_PRODUCT`→`CARE_SOLUTION`、`ProductUnit` 的 `SET`→`ITEM`、补充 `SUNGLASSES`、补充 `OUT_OF_STOCK` 和 `DELISTED` 状态 | 10 分钟 |

### 建议修改（WARNING — 提高代码质量）

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| 3 | Controller 操作人占位 | 4 个 Store Controller | 接入 `SecurityContextHolder` 获取真实用户 | 30 分钟 |
| 4 | 缺少 DDL | `docs/databases/ddl.sql` | 补充 10 张新表的建表语句 | 20 分钟 |
| 5 | API 请求风格不统一 | `frontend/src/views/store/index.vue` | 创建 `@/service/api` store 封装 | 15 分钟 |

### 可选修改（INFO — 锦上添花）

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| 6 | 订单号生成方式 | `StoreInventoryService.java` | 改用 `AtomicLong` 递增序列 | 5 分钟 |
| 7 | 格式化独立 commit | Git history | 将来单独 commit 格式化变更 | — |

---

## 7. 审核通过标准

阶段一代码需满足以下条件方可合并：

- [x] 安全配置脱敏完成（密码、密钥已移除）
- [ ] 前端页面可以正常渲染（修复 import + 枚举对齐）
- [ ] 前后端枚举值完全一致，创建商品/销售单无 400 错误
- [x] 后端事务逻辑正确，库存一致性有保障
- [x] 测试覆盖核心业务路径
- [ ] Controller 操作人解析不再返回硬编码值（至少添加 TODO 注释）
- [ ] DDL 建表语句已补充
- [x] 原有 RAG 聊天/文档功能未被破坏

---

*报告生成时间：2026-06-08 | 审查工具：Hermes Agent + Git Diff Analysis*
