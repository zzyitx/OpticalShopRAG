# 代码审查报告：阶段二 AI 结构化经营查询

> 审查日期：2026-06-19
> 审查分支：`HEAD`
> 审查人：Hermes Agent
> 关联文档：`docs/glasses-store/plans/glasses-store-phase2-ai-query-plan-draft.md`

---

## 1. 改动概览

### 1.1 统计数据

| 指标 | 数值 |
|---|---|
| 审查文件数 | 11 |
| 新增文件 | 4 (StoreQueryService.java, StoreQueryServiceTest.java, AgentToolRegistryTest.java, chat-message.vue 改动) |
| 修改文件 | 7 (AgentToolRegistry.java, LlmProviderRouter.java, ChatHandler.java, 4 StoreRepository, chat-message.vue, api.d.ts) |
| 新增代码行 | ~1,200 行（含测试） |

### 1.2 变更分类

**A. 新增核心服务**
- `StoreQueryService.java` — 统一只读查询门面，5 个查询方法 + 配套 Record 类

**B. Repository 扩展**
- `StoreProductRepository.searchProducts()` — 多字段模糊搜索
- `StoreInventoryStockRepository.searchStocks()` — 库存状态 + 预警筛选
- `StoreInventoryLedgerRepository.searchLedgers()` — 流水查询含日期范围
- `StoreSalesBillRepository.searchBills()` / `countByPurchaseDateBetween()` / `sumActualAmountByPurchaseDateBetween()`

**C. AI 工具注册**
- `AgentToolRegistry` 新增 5 个门店工具 + `toToolResult()` 转换 + 参数提取工具方法

**D. 系统提示词**
- `LlmProviderRouter.buildReActMessages()` 新增眼镜店业务数据规则段（7 条规则）

**E. 前端展示**
- `chat-message.vue` 新增 tool event UI（图标 + 名称 + 来源标签 + 状态）
- `api.d.ts` 新增 `AgentToolEvent` 类型定义
- `ChatHandler.sendToolCallStatus()` 发送 tool_call WebSocket 事件

**F. 测试**
- `StoreQueryServiceTest` — 6 个测试覆盖 5 个查询方法 + 边界条件
- `AgentToolRegistryTest` — 2 个测试覆盖工具注册和调用链路

---

## 2. 严重问题 (CRITICAL) — 阻止运行

### 2.1 前端 WebSocket `tool_call` 消息被静默丢弃，工具执行状态 UI 永不显示

**文件：** `frontend/src/store/modules/chat/index.ts` (行 364-376)
**严重程度：** CRITICAL
**影响范围：** 聊天页面 tool event UI（执行中/已完成/失败状态条）完全不可见

#### 问题描述

后端 `ChatHandler.sendToolCallStatus()` 通过 WebSocket 发送 `{type: "tool_call", tool: "...", status: "...", ...}`，但前端 chat store 的 `wsData` watcher 只处理了 `{type: "connection"}`（行 368），其他类型的消息（包括 `tool_call`、`chunk`、`completion`、`error`）全部被忽略。

`chat-message.vue` 依赖 `props.msg.toolEvents` 渲染工具状态 UI（行 388-402），但没有任何代码将 WebSocket 的 tool_call 事件写入消息的 `toolEvents` 数组。

```typescript
// 当前 wsData watcher（行 364-376）
watch(wsData, val => {
  if (!val) return;
  try {
    const data = JSON.parse(val);
    if (data.type === 'connection' && data.sessionId) {  // ← 只处理 connection
      handshakeConfirmed.value = true;
      sessionId.value = data.sessionId;
      syncGenerationAfterReconnect().catch(() => {});
    }
  } catch {
    // Ignore JSON parse errors
  }
});
```

#### 建议修改

在 `wsData` watcher 中增加对 `tool_call` 类型的处理，更新对应 `generationId` 的 assistant message 的 `toolEvents`：

```typescript
watch(wsData, val => {
  if (!val) return;
  try {
    const data = JSON.parse(val);
    if (data.type === 'connection' && data.sessionId) {
      handshakeConfirmed.value = true;
      sessionId.value = data.sessionId;
      syncGenerationAfterReconnect().catch(() => {});
    }
    // 新增：处理 tool_call 事件
    if (data.type === 'tool_call' && data.generationId) {
      const idx = findAssistantIndexByGenerationId(data.generationId);
      if (idx >= 0) {
        const msg = list.value[idx];
        if (!msg.toolEvents) {
          msg.toolEvents = [];
        }
        msg.toolEvents.push({
          id: data.toolCallId,
          tool: data.tool,
          status: data.status,
          timestamp: data.timestamp
        });
      }
    }
  } catch {
    // Ignore JSON parse errors
  }
});
```

同时，在 `upsertGenerationSnapshot` 中初始化 `toolEvents: []`（见 3.1）。

---

## 3. 警告问题 (WARNING) — 建议修复

### 3.1 `upsertGenerationSnapshot` 未初始化 `toolEvents`

**文件：** `frontend/src/store/modules/chat/index.ts` (行 85-100)
**严重程度：** WARNING
**影响范围：** 新创建的 assistant message 的 `toolEvents` 为 `undefined`

#### 问题描述

`chat-message.vue` 使用 `props.msg.toolEvents || []`（行 96），所以不会报错。但类型定义 `Api.Chat.Message.toolEvents` 标记为可选（`toolEvents?: AgentToolEvent[]`），未初始化的 `undefined` 是有歧义的。

#### 建议修改

```typescript
// line 85-100, add toolEvents: []
list.value.push({
  role: 'assistant',
  content: snapshot.content || '',
  status: nextStatus,
  conversationId: snapshot.conversationId,
  generationId: snapshot.generationId,
  timestamp: snapshot.updatedAt,
  referenceMappings: snapshot.referenceMappings,
  toolEvents: []  // ← 新增
});
```

### 3.2 `searchBills` 无约束全表查询风险

**文件：** `src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java` (行 114-116)
**严重程度：** WARNING
**影响范围：** 客户数据隐私

#### 问题描述

当 `querySalesBills` 的三个参数（customerPhone, customerName, billNo）均为 null 时，不会触发 "必须提供手机号" 的校验，会执行无筛选的账单查询。虽然 `MAX_LIMIT=50` 限制了返回数量，但仍可能返回 50 条最近账单，暴露客户隐私数据。

计划风险章节 8.3 明确要求："自然语言模糊查询返回无关客户数据...工具结果只返回回答问题所需字段"。

#### 建议修改

```java
// line 114-116, replace:
if (!StringUtils.hasText(customerPhone) && StringUtils.hasText(customerName) && !StringUtils.hasText(billNo)) {
    throw new CustomException("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
}

// with:
if (!StringUtils.hasText(customerPhone) && !StringUtils.hasText(billNo)) {
    throw new CustomException("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
}
```

这样确保查询销售账单时必须提供手机号或账单号，防止无约束查询。

### 3.3 系统提示词仅在 `buildReActMessages` 路径生效

**文件：** `src/main/java/com/yizhaoqi/smartpai/service/LlmProviderRouter.java` (行 143-150)
**严重程度：** WARNING
**影响范围：** 非 ReAct 聊天路径

#### 问题描述

眼镜店业务数据规则（"眼镜店结构化业务数据规则"）只在 `buildReActMessages` 中注入。如果存在不使用 ReAct 的聊天路径（`streamResponse` → `buildMessages`），该路径不会包含这些规则，模型可能不会调用门店工具。

#### 建议修改

将眼镜店规则提取为独立方法，在 `buildMessages` 和 `buildReActMessages` 中都注入，或者确认项目已完全迁移到 ReAct 路径后添加注释说明。

### 3.4 眼镜店场景默认问题未实现

**文件：** 前端配置或提示词相关文件
**严重程度：** WARNING
**影响范围：** 用户体验引导

#### 问题描述

计划批次 3 明确要求"增加眼镜店场景默认问题示例"，如 "镜框 A123 还有多少库存？" "低于安全库存的商品有哪些？" 等。搜索结果中未找到对应的默认问题配置。

#### 建议修改

在聊天输入框下方或预设问题配置中添加眼镜店场景快捷问题：

```typescript
// 示例：frontend/src/views/chat 相关配置
const glassesStoreQuickQuestions = [
  "镜框 A123 还有多少库存？",
  "低于安全库存的商品有哪些？",
  "查询 A123 最近的出入库流水",
  "张三上次配镜的左右眼度数是多少？",
  "今天有多少张账单，实收金额是多少？"
];
```

---

## 4. 信息提示 (INFO) — 值得注意但无害

### 4.1 日期范围计算偏移量可读性

**文件：** `StoreQueryService.java` (行 234, 238)
**内容：** `minusDays(DEFAULT_DATE_DAYS - 1L)` 和 `plusDays(MAX_DATE_DAYS - 1L)` 的 `-1L` 偏移容易让读者困惑。建议提取为命名常量或添加注释。

### 4.2 统计查询结果使用 `List.of(stats)` 包装

**文件：** `StoreQueryService.java` (行 146)
**内容：** 经营统计始终返回 `List.of(stats)`，虽然单元素列表工作正常，但从语义上看统计是单值结果。可考虑增加 `resultSingle()` 重载。

### 4.3 Repository 新增方法缺少 Javadoc

**文件：** 4 个 StoreRepository
**内容：** `searchProducts`、`searchStocks`、`searchLedgers`、`searchBills` 方法缺少 Javadoc，不符合 `AGENTS.md` 的代码注释标准。

---

## 5. 正面评价 — 做得好的设计

- **StoreQueryService 架构清晰**：Java Record 定义查询参数和结果视图，强类型约束，不可变数据
- **只读事务约束**：所有查询方法标记 `@Transactional(readOnly = true)`，确保 AI 工具不会修改业务数据
- **参数边界保护**：`resolveLimit` 和 `resolveDateRange` 方法统一处理默认值和最大值，防止上下文溢出
- **来源标签一致**：每个查询结果都携带 `source`/`sourceLabel`/`content`，在 AgentToolRegistry 中统一封装为 `ToolExecutionResult`
- **系统提示词质量高**：7 条眼镜店业务规则清晰、可操作，明确区分了"数据库事实"和"文档知识"的优先级
- **前端 UI 设计精细**：tool event 的状态图标、来源标签样式（绿色背景标签）、成功/失败边框颜色区分，交互细节到位
- **测试覆盖关键路径**：5 个查询方法都有测试，包含正常返回、空结果边界和客户手机号校验
- **Repository JPQL 参数化**：所有查询使用 `@Param` 和命名参数，无 SQL 注入风险
- **前端 TypeScript 类型完备**：`AgentToolEvent` 类型定义了 `executing | success | failed` 联合类型，`toolEvents` 正确挂载到 `Chat.Message` 上

---

## 6. 修改建议汇总

### 必须修改 (CRITICAL)

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| C1 | tool_call 事件前端丢失 | `chat/index.ts` | wsData watcher 增加 tool_call 处理 + 更新 toolEvents | 30 min |

### 建议修改 (WARNING)

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| W1 | upsertGenerationSnapshot 未初始化 toolEvents | `chat/index.ts` | 添加 `toolEvents: []` | 5 min |
| W2 | searchBills 无约束查询风险 | `StoreQueryService.java` | 收紧手机号/账单号必填校验 | 10 min |
| W3 | 眼镜店规则仅在 ReAct 路径生效 | `LlmProviderRouter.java` | 确认是否需要在非 ReAct 路径也注入 | 15 min |
| W4 | 眼镜店场景默认问题未实现 | 前端配置 | 添加快捷问题预设 | 20 min |

### 可选修改 (INFO)

| # | 问题 | 文件 | 修改内容 | 预计工作量 |
|---|------|------|---------|-----------|
| I1 | 日期计算偏移量可读性 | `StoreQueryService.java` | 提取命名常量 | 5 min |
| I2 | 统计结果单元素包装 | `StoreQueryService.java` | 增加 resultSingle() | 15 min |
| I3 | Repository 方法缺 Javadoc | 4 个 StoreRepository | 添加方法注释 | 15 min |

---

## 7. 审核通过标准

**与计划对照：**

- [x] 五个门店查询工具均已注册（query_product / query_inventory / query_stock_flow / query_sales_bill / query_store_stats）
- [x] StoreQueryService 提供统一只读查询面，参数校验和限制完整
- [x] Repository 层扩展了结构化搜索能力
- [x] 系统提示词明确区分数据库查询和 RAG 查询优先级
- [x] 前端 tool event UI 组件已构建（名称映射、状态图标、来源标签）
- [x] 前端 TypeScript 类型定义到位
- [x] 后端测试覆盖 5 个查询方法的正常场景和边界
- [ ] **前端 tool_call WebSocket 事件未接入 chat store**（CRITICAL）
- [ ] 眼镜店场景默认问题示例未添加（WARNING）
- [ ] 销售账单查询的隐私约束不够严格（WARNING）

**合并前必须完成：**

- [x] 后端测试、编译通过
- [ ] **修复 C1：前端 tool_call 事件处理**（阻塞）
- [ ] 修复 W2：收紧 searchBills 查询约束
- [ ] 浏览器端到端验证：确认 tool event UI 在页面上实际可见
- [ ] 浏览器验收问题清单逐项验证（计划第 6 批次所列 8 个问题）

---

*报告生成时间：2026-06-19 | 审查工具：Hermes Agent + Git Diff Analysis*
