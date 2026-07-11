# 运行态验收与操作手册 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在真实本地运行态完成基础资料、两种采购、FEFO 销售、MySQL 数量守恒和 AI 查询验收，并基于最终真实页面编写 Markdown 操作流程手册。

**Architecture:** 通过业务页面或 API 创建带唯一前缀的验收数据，浏览器网络与 MySQL 双向核对；截图只保存脱敏后的真实页面。操作手册使用最终菜单、字段和截图，不使用设计原型替代运行证据。

**Tech Stack:** IDEA 热部署、Vue/Vite、Chrome CDP、Spring Boot REST API、MySQL CLI、Markdown、PowerShell。

---

## 文件结构

### 新增

- `docs/glasses-store/manuals/glasses-store-operation-manual.md`
- `docs/glasses-store/manuals/images/01-login.png`
- `docs/glasses-store/manuals/images/02-master-data.png`
- `docs/glasses-store/manuals/images/03-product.png`
- `docs/glasses-store/manuals/images/04-purchase-direct.png`
- `docs/glasses-store/manuals/images/05-purchase-receipt.png`
- `docs/glasses-store/manuals/images/06-sales-fefo.png`
- `docs/glasses-store/manuals/images/07-inventory-batch.png`
- `docs/glasses-store/manuals/images/08-dashboard.png`
- `docs/glasses-store/manuals/images/09-ai-query.png`
- `docs/glasses-store/implementation/glasses-store-workflow-upgrade-acceptance-2026-07-11.md`
- `docs/databases/store-workflow-acceptance.sql`

### 修改

- `docs/glasses-store/README.md`：增加设计、四阶段计划、操作手册和验收记录链接。

## Task 1：建立新鲜的静态与运行态基线

**Files:** 只新增验收记录文件，不修改业务代码。

- [ ] **Step 1：运行完整 Store 定向回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=Store*Test,AgentToolRegistryTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
Set-Location frontend
pnpm.CMD typecheck
Set-Location ..
```

Expected: Store 定向测试、后端编译和前端 typecheck 全部通过。若全量 `mvn test` 存在无关基线失败，单独记录，不能把定向通过写成全量通过。

- [ ] **Step 2：核对真实监听端口**

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -in 3306,6379,8081,9000,9092,9200,9527 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

Expected: `3306`、`8081`、`9527` 可用；Redis、Elasticsearch、Kafka 和 MinIO 按当前 `.env` 实际地址核对。后端不在时由用户从 IDEA 启动，前端不在时从 VS Code 任务或 `pnpm.CMD dev` 启动，不擅自重启已有进程。

- [ ] **Step 3：创建验收 SQL**

`docs/databases/store-workflow-acceptance.sql` 必须包含以下只读查询：

```sql
SELECT 'unit' object_type, COUNT(*) object_count FROM store_measurement_unit
UNION ALL SELECT 'category', COUNT(*) FROM store_product_category
UNION ALL SELECT 'partner', COUNT(*) FROM store_business_partner
UNION ALL SELECT 'warehouse', COUNT(*) FROM store_warehouse
UNION ALL SELECT 'product', COUNT(*) FROM store_product;

SELECT s.product_sku, s.warehouse_code, s.current_quantity,
       COALESCE(SUM(b.current_quantity), 0) batch_quantity
FROM store_inventory_stock s
LEFT JOIN store_inventory_batch b
  ON b.product_sku = s.product_sku AND b.warehouse_code = s.warehouse_code
GROUP BY s.id
HAVING s.current_quantity <> COALESCE(SUM(b.current_quantity), 0);

SELECT business_order_no, product_sku, batch_no, quantity_before,
       change_quantity, quantity_after
FROM store_inventory_ledger
WHERE quantity_before + change_quantity <> quantity_after;
```

- [ ] **Step 4：记录执行前基线**

在验收报告写入 Git HEAD、工作树状态、端口、Store 表行数、默认仓库数量和测试前缀 `UAT-20260711-` 是否已存在。Expected: 没有同前缀遗留；发现遗留时先确认来源，不直接删除。

## Task 2：通过真实页面完成首次配置

**Files:** 验收报告与截图 01-03。

- [ ] **Step 1：打开并登录真实页面**

使用 `browser-cdp` skill 复用 Chrome 登录态，打开：

```text
http://localhost:9527/#/store
```

检查 Console 无新增错误，Network 中 `/proxy-default/api/v1/store/dashboard/summary` 返回 HTTP 200。保存脱敏截图 `01-login.png`。

- [ ] **Step 2：创建基础资料**

在“基础资料”依次确认或创建：

```text
单位：UAT-20260711-CARTON / 箱
一级类别：UAT-20260711-CONTACT / 验收隐形眼镜
二级类别：UAT-20260711-HALF-YEAR / 验收半年抛
供应商：UAT-20260711-SUPPLIER / UAT-20260711-验收供应商
客户：UAT-20260711-RETAIL / UAT-20260711-验收零售客户 / 16600000011
默认仓库：只核对系统现有 DEFAULT，不修改仓库名称和负责人
```

保存 `02-master-data.png`，Network 证据记录请求路径、HTTP 状态和返回 ID，不保存 Token/Cookie。

- [ ] **Step 3：创建两个商品**

```text
批次商品：SKU 留空自动生成，名称 UAT-20260711-半年抛-3.00，单位 盒，分类 验收半年抛，批次/有效期管理开启，安全库存 2
非批次商品：SKU 留空自动生成，名称 UAT-20260711-镜盒，单位 个，分类 其他，批次管理关闭，安全库存 1
```

保存 `03-product.png`，记录系统生成的两个 SKU 到验收报告。

- [ ] **Step 4：MySQL 核对主数据**

执行验收 SQL 的主数据查询。Expected: 新商品关联正确的 category/unit/supplier，启用默认仓库恰好 1 个，非批次商品配置为不管理批次。

## Task 3：验收两种采购路径

**Files:** 验收报告与截图 04-05。

- [ ] **Step 1：直接入库**

创建采购订单：供应商 `UAT-20260711-SUPPLIER`、模式“直接入库”、批次商品数量 5、批号 `UAT-20260711-B001`、生产日期 `2026-07-01`、有效期 `2027-07-01`；审核确认。

Expected:

- 采购状态为“已入库”。
- `UAT-20260711-B001` 批次库存为 5。
- SKU 汇总库存为 5。
- 产生一条 `PURCHASE_ORDER` 入库流水，`0 + 5 = 5`。
- 重复点击审核不会再次增加库存。

保存 `04-purchase-direct.png`。

- [ ] **Step 2：后续分次验收**

创建采购订单：模式“后续验收”、同一批次商品订购 6；审核后库存不变。第一次验收 2，批号 `UAT-20260711-B002`、有效期 `2026-12-31`；第二次验收 4，批号 `UAT-20260711-B003`、有效期 `2028-12-31`。

Expected:

- 订单依次为“待到货 → 部分验收 → 全部完成”。
- 第一次确认后汇总库存 7，第二次后汇总库存 11。
- 三个批次数量分别为 5、2、4。
- 尝试再验收 1 件返回超量错误，库存不变。

保存 `05-purchase-receipt.png`。

- [ ] **Step 3：非批次商品直接入库**

为 `UAT-20260711-镜盒` 直接入库 3，不填写批号和有效期。Expected: 页面不要求批次字段；底层 `NO_BATCH` 数量和 SKU 汇总库存均为 3，页面不显示 `NO_BATCH`。

- [ ] **Step 4：执行采购后 SQL**

Expected: 汇总库存与批次库存差异查询为 0 行，流水等式异常为 0 行。

## Task 4：验收 FEFO 销售和客户历史

**Files:** 验收报告与截图 06-07。

- [ ] **Step 1：创建销售草稿并检查 FEFO**

选择客户 `16600000011`，销售批次商品数量 3。Expected: 系统先分配有效期更早的 `UAT-20260711-B002` 数量 2，再分配 `UAT-20260711-B001` 数量 1；草稿保存后库存仍为 11。

- [ ] **Step 2：人工改选并审核**

把第二行从 `UAT-20260711-B001` 改选为 `UAT-20260711-B003` 数量 1，审核确认。

Expected:

- 销售状态为“已出库”。
- `UAT-20260711-B002` 变为 0，`UAT-20260711-B003` 变为 3，`UAT-20260711-B001` 保持 5。
- SKU 汇总库存从 11 变为 8。
- 销售流水变化量合计为 -3。
- 重复审核不再次扣库存。

保存 `06-sales-fefo.png` 和 `07-inventory-batch.png`。

- [ ] **Step 3：验证阻断规则**

分别尝试销售数量 99、选择已过期测试批次、使用停用商品。Expected: 都在审核前或审核事务中被拒绝，库存和流水没有部分变化。

- [ ] **Step 4：验证客户历史**

按 `16600000011` 查询，Expected: 返回本次销售；仅凭客户姓名的 AI 查询被要求补充手机号或单据号。

## Task 5：验收经营台和 AI 只读查询

**Files:** 验收报告与截图 08-09。

- [ ] **Step 1：核对经营台**

打开经营台，Expected: 基础资料完成、待验收归零、低库存/近效期数量与 MySQL 查询一致。保存 `08-dashboard.png`。

- [ ] **Step 2：执行结构化 AI 问题**

在聊天页面依次询问：

```text
有哪些采购订单还在等待验收？
UAT-20260711-半年抛-3.00 目前各批次库存是多少？
未来 180 天内有哪些批次到期？
手机号 16600000011 的购买记录是什么？
```

Expected: 前三类回答分别调用采购、批次库存工具；客户问题调用销售账单工具；回答包含 MySQL 结构化来源，不用 RAG 文档数量冒充实时库存。

- [ ] **Step 3：验证 RAG 边界**

询问商品说明书或资质问题。Expected: 使用知识库引用；AI 不声称可以代替店员审核单据。保存 `09-ai-query.png`，截图中手机号保留为测试号码，Token/Cookie 必须裁掉。

## Task 6：编写 Markdown 操作流程手册

**Files:** `docs/glasses-store/manuals/glasses-store-operation-manual.md` 及九张截图。

- [ ] **Step 1：写手册固定结构**

手册必须使用以下章节，不得出现“待补充”或假截图：

```markdown
# PaiSmart 眼镜店操作流程手册

## 1. 适用范围与角色
## 2. 登录与进入经营台
## 3. 首次配置总览
## 4. 新增计量单位
## 5. 新增一级、二级商品类别
## 6. 新增客户和供应商
## 7. 配置默认仓库
## 8. 新增商品与自动 SKU
## 9. 采购订单直接入库
## 10. 采购订单后续验收
## 11. 销售订单与 FEFO 批次选择
## 12. 库存、批次与流水查询
## 13. 经营台风险提醒
## 14. AI 查询示例与数据来源
## 15. 异常处理
## 16. 单据状态说明
## 17. 岗位权限表
## 18. 日常操作检查表
```

- [ ] **Step 2：写每个操作章节的统一格式**

每章包含“使用场景、前置条件、菜单路径、编号步骤、填写示例、成功标志、常见错误、相关截图”。采购和销售章节必须明确“保存草稿不改库存，审核/验收确认才改库存”。

- [ ] **Step 3：写异常处理表**

至少覆盖：库存不足、过期批次、超量验收、重复审核、资料停用、手机号缺失、权限不足、后端/前端未启动。每项写出页面提示、原因和安全处理方法；禁止建议直接改数据库绕过业务规则。

- [ ] **Step 4：逐章复走**

由手册从第 2 章开始逐步操作一次，记录任何菜单名、按钮名或字段名不一致并立即修正文档。Expected: 手册可在不阅读源码的情况下完成首次配置、两种采购和一笔销售。

## Task 7：完成验收报告、清理与最终提交

**Files:** 验收报告、README、手册、截图、SQL。

- [ ] **Step 1：完成门槛式验收结论**

报告分别给出 `code`、`tests`、`runtime`、`data`、`browser`、`AI/RAG`、`manual` 七层结论；任何一层无证据时不得写“完成”。

- [ ] **Step 2：定向清理验收数据**

先用测试前缀和捕获的主键只读预览。草稿通过业务接口取消；已确认的测试业务数据按外键顺序在事务内定向清理。若目标集合包含非 `UAT-20260711-` 资料或非本轮捕获 ID，立即回滚。

- [ ] **Step 3：复核基线**

再次执行验收 SQL。Expected: 本轮测试资料、单据、批次、流水和销售记录均已清理，非测试数据行数回到执行前基线。

- [ ] **Step 4：最终静态验证**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=Store*Test,AgentToolRegistryTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
Set-Location frontend
pnpm.CMD typecheck
Set-Location ..
git diff --check
```

Expected: 全部通过。

- [ ] **Step 5：更新文档索引并提交**

`docs/glasses-store/README.md` 增加设计规格、四份计划、操作手册和验收报告链接。只暂存手册、脱敏截图、验收 SQL、验收报告和索引：

```powershell
git commit -m "docs(store): 交付眼镜店操作手册与验收证据" -m "验证:`n- Store 定向测试与编译`n- 前端 typecheck`n- 浏览器与 MySQL 双向核验`n- 操作手册逐章复走"
```
