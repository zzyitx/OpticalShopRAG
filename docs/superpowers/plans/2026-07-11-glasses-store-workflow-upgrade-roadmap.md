# 眼镜店单店进销存升级实施路线图

> 设计规格：[`../specs/2026-07-11-glasses-store-procurement-sales-workflow-design.md`](../specs/2026-07-11-glasses-store-procurement-sales-workflow-design.md)

本次升级覆盖四个可独立验收的子系统。为避免主数据、采购库存和销售出库在同一次大改中互相干扰，必须按以下顺序执行；前一阶段的定向测试、编译和数据校验通过后，才能进入下一阶段。

| 顺序 | 计划 | 独立完成条件 |
| --- | --- | --- |
| 1 | [`2026-07-11-glasses-store-master-data.md`](2026-07-11-glasses-store-master-data.md) | 单位、两级分类、往来单位、默认仓库和商品新关联可维护；旧商品可兼容读取 |
| 2 | [`2026-07-11-glasses-store-purchase-batch-inventory.md`](2026-07-11-glasses-store-purchase-batch-inventory.md) | 直接入库、后续分次验收、批次库存和 SKU 汇总库存数量守恒 |
| 3 | [`2026-07-11-glasses-store-sales-dashboard-ai.md`](2026-07-11-glasses-store-sales-dashboard-ai.md) | 销售草稿/审核、FEFO 批次扣减、经营台待办和 AI 只读查询通过 |
| 4 | [`2026-07-11-glasses-store-runtime-manual.md`](2026-07-11-glasses-store-runtime-manual.md) | 浏览器、网络、MySQL 和 AI 证据完整，Markdown 操作手册可逐步复走 |

## 执行前工作树门槛

当前工作区已有一组尚未提交的 Store 读路径治理代码，覆盖 `StoreInventoryController`、`StoreInventoryService`、`StoreQueryService`、仓储和测试；另有 IDE、配置、工具目录及验收资料改动。开始阶段 1 前必须先审查并处置这组已有改动，不能让后续实现覆盖或顺带提交它们。

执行以下命令：

```powershell
git status --short --branch
git diff -- src/main/java/com/yizhaoqi/smartpai/controller/StoreInventoryController.java
git diff -- src/main/java/com/yizhaoqi/smartpai/service/StoreInventoryService.java
git diff -- src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java
git diff -- src/test/java/com/yizhaoqi/smartpai/service/StoreInventoryServiceTest.java
```

预期：执行者能明确区分“读路径治理”“本次新阶段”“IDE/本地配置/工具产物”三类文件。若读路径治理仍未完成，先按 `docs/superpowers/plans/2026-06-30-store-read-path-cleanup.md` 验证并单独提交；不得使用 `git add .`、`git add -A` 或重置用户改动。

## 公共工程规则

- 后端先写失败测试，再写最小实现。
- 所有新表、列和持久化实体字段必须有中文业务注释。
- 单据确认和库存变化必须处于同一事务，并使用必要的悲观锁。
- Controller 权限和前端按钮权限必须同时更新。
- 后端列表默认分页，禁止重新引入无边界 `findAll()`。
- Java 改动后使用 `mvn -q -DskipTests compile` 触发 IDEA 热加载，不默认重启后端。
- 前端改动后使用 `pnpm.CMD typecheck`，并在 `http://localhost:9527` 真实页面验收。
- 每次提交只暂存当前阶段的明确文件，排除 `.vscode`、`.iml`、`.codex-tmp`、`.superpowers`、`.understand-anything`、原始测试资料和其他任务改动。

## 阶段间数据契约

- 阶段 1 提供 `categoryId`、`unitId`、`defaultSupplierId`、`warehouseCode` 和商品批次管理配置。
- 阶段 2 提供批次库存写接口、采购状态和带批次的库存流水。
- 阶段 3 只通过阶段 2 的批次库存服务扣减库存，不重复实现库存事务。
- 阶段 4 只通过页面或业务 API 创建验收数据；MySQL 仅用于只读核验和最终定向清理。
