# 眼镜店改造阶段二计划稿：AI 结构化经营查询

> 状态：待审阅
> 日期：2026-06-13
> 前置条件：完成阶段一真实运行态验收
> 关联文档：
> - `../requirements/glasses-store-rag-prd.md`
> - `glasses-store-rag-implementation-plan.md`
> - `../implementation/glasses-store-phase1-readiness-audit-2026-06-12.md`

## 1. 阶段二范围判断

当前文档中存在三种可能的“阶段二”口径：

1. 总实施计划中的“阶段二：仓储业务闭环”已经在阶段一代码中完成，不应重复实施。
2. PRD 中的“二期：企业级增强”包含多门店、多仓库、盘点、审核流、供应商和成本分析，会引起核心数据模型重构，当前直接进入风险较高。
3. 阶段一实施方案将 AI 结构化工具接入延后到业务闭环稳定之后，适合作为当前阶段二的主要目标。

本计划建议将阶段二定义为：

> 基于阶段一已经落地的商品、库存、流水和销售账单数据，建设眼镜店 AI 结构化经营查询能力，并完成阶段一真实运行态收口。

PRD 中的企业级增强内容单独进入后续阶段规划。

## 2. 阶段目标

让 AI 优先查询 MySQL 中的权威业务数据，并在需要时结合 RAG 文档作答：

- 查询商品档案、型号、品牌和价格。
- 查询实时库存、低库存和缺货商品。
- 查询入库、出库和库存流水。
- 查询销售账单、客户历史配镜和度数变化。
- 查询今日或指定时间范围的经营统计。
- 回答中明确区分“业务数据库来源”和“知识库文档来源”。

## 3. 实施边界

### 3.1 本阶段包含

- 完成阶段一真实运行态验收。
- 新增统一的门店只读查询服务。
- 新增五个 AI 门店结构化查询工具。
- 优化 AI 工具选择规则和眼镜店场景提示词。
- 补充聊天页面中门店工具名称、执行状态和来源展示。
- 完成数据库、接口、AI 回答和浏览器端到端验证。

### 3.2 本阶段不包含

- AI 修改商品、库存、流水或账单。
- 多门店、多仓库和库存调拨。
- 入库、出库审核流。
- 盘点和库存调整。
- 独立供应商管理。
- 成本利润权限体系。
- 大规模经营台页面视觉重构。

## 4. 总体技术方案

新增统一只读查询门面 `StoreQueryService`，负责参数校验、查询范围限制、业务数据查询、结果格式化和来源说明。

`AgentToolRegistry` 负责定义工具协议并调用查询门面，不直接拼接 Repository 查询，避免 AI 接入逻辑侵入现有交易服务。

所有门店查询工具必须满足：

- 只读运行，不产生业务数据修改。
- 要求已登录用户身份。
- 限制最大返回数量。
- 日期范围设置默认值和最大跨度。
- 无数据时明确返回“未查询到记录”。
- 同时返回结构化 `data` 和适合模型阅读的 `content`。
- 标记来源表或业务模块，不伪装成文档引用。

## 5. AI 工具设计

| 工具 | 查询能力 | 主要参数 |
| --- | --- | --- |
| `query_product` | 查询商品档案和型号信息 | SKU、名称、品牌、型号、分类、返回数量 |
| `query_inventory` | 查询实时库存、低库存和缺货商品 | SKU、库存状态、是否仅看预警、返回数量 |
| `query_stock_flow` | 查询入库、出库和库存流水 | SKU、业务单号、流水类型、开始日期、结束日期 |
| `query_sales_bill` | 查询客户历史配镜、账单、金额和商品 | 手机号、客户名、账单号、开始日期、结束日期 |
| `query_store_stats` | 查询门店经营统计 | 开始日期、结束日期、统计维度 |

### 5.1 数据来源规则

- 商品事实来源：商品档案。
- 库存事实来源：库存表和库存流水。
- 客户历史和金额事实来源：销售账单。
- 经营统计来源：销售账单、库存和流水聚合结果。
- 产品说明、适用人群、售后政策等知识来源：RAG 文档。
- 混合问题允许先查业务数据库，再查 RAG 文档并汇总。

### 5.2 来源展示规则

结构化查询结果使用业务来源标签，例如：

- `来源：商品档案`
- `来源：实时库存`
- `来源：库存流水`
- `来源：销售账单`
- `来源：门店经营统计`

RAG 文档继续使用现有文档引用编号和引用预览链路。结构化业务来源不得生成虚假的文件名、页码或文档引用链接。

## 6. 实施批次

### 批次 0：阶段一真实运行态验收

目标：确认阶段二依赖的业务数据闭环在真实运行态可靠可用。

实施内容：

- 启动并确认 Kafka、Elasticsearch、MySQL 和 Redis。
- 确认后端 `8081` 和前端 `9527` 持续运行。
- 在浏览器完成商品创建、入库确认、自动销售出库、库存流水、客户历史和 Excel 导入验证。
- 核对页面结果、接口响应和 MySQL 数据一致。

进入下一批次的门槛：

- 阶段一收尾审查文档中的真实运行态验收清单全部通过。
- 不存在影响商品、库存、流水和账单查询准确性的阻断问题。

### 批次 1：门店只读查询能力

目标：建立可被 API 和 AI 工具复用的权威业务查询层。

建议新增：

- `src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreQueryServiceTest.java`

建议修改：

- `StoreProductRepository`
- `StoreInventoryStockRepository`
- `StoreInventoryLedgerRepository`
- `StoreInboundOrderRepository`
- `StoreOutboundOrderRepository`
- `StoreSalesBillRepository`

主要工作：

- 增加按业务字段筛选的 Repository 查询。
- 增加日期范围和经营统计查询。
- 定义统一查询请求和结果 DTO。
- 为所有列表查询设置最大返回数量。
- 提供客户历史度数变化的结构化结果。
- 提供低库存、缺货和指定 SKU 库存查询。

关键规则：

- 禁止直接使用无上限 `findAll()` 向模型返回全量数据。
- 客户历史优先按手机号精确匹配，姓名仅用于辅助搜索。
- 金额使用 `BigDecimal`，日期使用明确格式。
- 查询结果不得暴露与用户问题无关的敏感字段。

### 批次 2：AI 工具接入

目标：让现有 ReAct 工具调用链可以查询门店权威业务数据。

建议修改：

- `src/main/java/com/yizhaoqi/smartpai/service/AgentToolRegistry.java`
- `src/main/java/com/yizhaoqi/smartpai/service/LlmProviderRouter.java`

建议新增：

- `src/test/java/com/yizhaoqi/smartpai/service/AgentToolRegistryTest.java`

主要工作：

- 在 `AgentToolRegistry` 注册五个门店工具。
- 增加工具参数 Schema、参数校验和执行处理器。
- 调用 `StoreQueryService` 获取结构化结果。
- 为工具结果添加业务来源标签。
- 修改模型系统提示词，明确数据库查询和 RAG 查询的选择规则。

提示词必须明确：

- 库存、账单和经营金额等事实优先查询 MySQL。
- 产品说明、适用人群和售后政策等知识优先查询 RAG。
- 混合问题可以组合调用两类工具。
- 无数据时必须说明未查询到，不得编造。

### 批次 3：聊天页面和眼镜店场景体验

目标：让用户能够理解 AI 正在查询什么，以及答案依据来自哪里。

建议修改：

- `frontend/src/views/chat/modules/chat-message.vue`
- 相关聊天提示词或默认问题配置。

主要工作：

- 增加五个门店工具的中文名称映射。
- 展示门店工具执行中、成功和失败状态。
- 展示结构化业务来源标签。
- 保留现有知识库文档引用预览链路。
- 增加眼镜店场景默认问题示例。
- 清理本次涉及路径中遗留的临时调试输出。

### 批次 4：测试与端到端验收

后端测试范围：

- `StoreQueryServiceTest`
- `AgentToolRegistryTest`
- Store Repository 查询测试
- 空结果和错误参数测试
- 日期范围、最大返回数量和敏感字段限制测试
- 商品、库存、流水、账单和统计工具调用测试
- 所有 AI 门店工具只读性测试

验证命令：

```powershell
mvn -q -DskipTests compile
mvn -q -Dtest=Store*Test,AgentToolRegistryTest test
cd frontend
pnpm typecheck
pnpm exec eslint src/views/chat/modules/chat-message.vue
git diff --check
```

浏览器验收问题：

- “镜框 A123 还有多少库存？”
- “低于安全库存的商品有哪些？”
- “查询 A123 最近的出入库流水。”
- “张三上次配镜的左右眼度数是多少？”
- “张三这次度数和上次相比有什么变化？”
- “今天有多少张账单，实收金额是多少？”
- “这款防蓝光镜片适合什么人？”
- “查询该镜片库存，并结合说明书告诉我是否适合儿童。”

每个问题均需确认：

- AI 是否调用了正确工具。
- 工具返回是否与 MySQL 权威数据一致。
- 回答是否展示正确来源。
- 混合问题是否同时保留文档引用能力。
- 浏览器控制台和网络请求是否无明显错误。

## 7. 预计文件范围

### 新增文件

- `src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreQueryServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/AgentToolRegistryTest.java`

### 修改文件

- Store 相关 Repository。
- `src/main/java/com/yizhaoqi/smartpai/service/AgentToolRegistry.java`
- `src/main/java/com/yizhaoqi/smartpai/service/LlmProviderRouter.java`
- `frontend/src/views/chat/modules/chat-message.vue`
- 相关聊天提示词或默认问题配置。
- 阶段二实施和验收文档。

## 8. 风险与控制措施

### 8.1 查询结果过大

风险：模型上下文被大量商品、流水或账单占满。

控制：

- 所有工具设置默认数量和最大数量。
- 日期范围设置默认值和最大跨度。
- 统计问题优先返回聚合结果，而不是完整明细。

### 8.2 AI 混淆数据库事实和文档知识

风险：模型把 RAG 文档中的历史库存或报价当作实时事实。

控制：

- 在工具描述和系统提示词中明确来源优先级。
- 结构化工具返回固定业务来源标签。
- 混合回答分别说明数据库事实和文档依据。

### 8.3 客户数据暴露

风险：自然语言模糊查询返回无关客户数据。

控制：

- 客户历史优先要求手机号精确查询。
- 姓名模糊搜索限制结果数量。
- 工具结果只返回回答问题所需字段。
- 不在工具结果中返回无关审计或内部字段。

### 8.4 现有聊天与引用功能回归

风险：新增结构化工具影响现有 `search_knowledge` 和文档引用预览。

控制：

- 保持 `search_knowledge` 的结果结构和引用映射逻辑不变。
- 结构化来源不进入文档引用映射。
- 增加混合查询端到端验收。

## 9. 完成标准

阶段二完成必须同时满足：

- 五个门店查询工具均可由 AI 正确调用。
- AI 不使用 RAG 文档内容冒充实时库存或账单事实。
- AI 回答明确展示业务数据来源。
- 无数据、参数错误和服务异常时不编造结果。
- 现有知识库引用预览和聊天历史功能无回归。
- 后端测试、前端类型检查和格式检查通过。
- 浏览器完成结构化查询、混合查询和 MySQL 数据核对。

## 10. 审阅决策点

请重点审阅以下范围决策：

1. 是否同意将阶段二定义为“AI 结构化经营查询”，将 PRD 企业级增强延后。
2. 是否同意阶段二的 AI 工具全部保持只读，不支持自然语言修改业务数据。
3. 是否同意客户历史以手机号精确查询为主，姓名仅作为受限辅助搜索。
4. 是否同意必须先完成阶段一真实运行态验收，再开始阶段二代码实施。
