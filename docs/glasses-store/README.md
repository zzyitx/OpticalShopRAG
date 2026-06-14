# 眼镜店改造文档索引

本目录收纳 PaiSmart 眼镜店实体店改造相关文档，按“需求、计划、实施方案”分类管理，避免后续阶段一开发资料散落在 `docs/` 根目录。

## 目录结构

| 分类 | 路径 | 用途 |
| --- | --- | --- |
| 需求稿 | [requirements/glasses-store-rag-prd.md](requirements/glasses-store-rag-prd.md) | 记录产品定位、业务边界、功能范围、验收标准。 |
| 计划稿 | [plans/glasses-store-rag-implementation-plan.md](plans/glasses-store-rag-implementation-plan.md) | 记录总体实施路线、模块拆分、接口范围、测试计划。 |
| 实施方案稿 | [implementation/glasses-store-phase1-implementation-draft.md](implementation/glasses-store-phase1-implementation-draft.md) | 记录阶段一首批落地方案、配置脱敏、后端闭环、最小前端验证路径。 |
| 阶段一收尾审查 | [implementation/glasses-store-phase1-readiness-audit-2026-06-12.md](implementation/glasses-store-phase1-readiness-audit-2026-06-12.md) | 记录阶段一已关闭问题、验证证据和最终运行态验收清单。 |

## 阅读顺序

1. 先读需求稿，确认阶段一业务边界。
2. 再读计划稿，理解整体分阶段路线。
3. 最后读实施方案稿，作为当前阶段一编码和验收的直接依据。

## 当前阶段一边界

- 单店单仓。
- 少于 5 人的小型眼镜实体店。
- 无审批流。
- 无真实支付对接。
- 不做独立客户档案，客户配镜历史随账单保留。
- 库存、账单、客户配镜记录以 MySQL 为准；AI 查询业务数据时应优先查数据库，再结合 RAG 文档。
