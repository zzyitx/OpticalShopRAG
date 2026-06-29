# Store 读路径治理设计

## 背景

PaiSmart 已经在 `main` 合入阶段二结构化门店查询能力。当前 Store 业务链包括商品档案、库存、入库单、出库单、库存流水、销售账单、经营统计，以及面向 AI 工具的只读查询服务。

下一步整理应先加固 Store 读路径，再进入第三阶段智能经营能力。现有库存和销售账单写路径已经有较清晰的事务边界，因此本设计明确避开库存变更、账单创建和支付相关逻辑。

## 目标

- 清除 Store 读路径中的高风险全量读取，尤其是仍会加载整表的列表方法。
- 保持 AI 门店工具查询有上限、有来源、可测试。
- 保留销售账单隐私边界：客户历史查询必须提供 `customerPhone` 或 `billNo`。
- 精简方法职责，不做大范围重构，也不引入新的查询基础设施。
- 前端视觉结构保持不变；只有后端响应结构确实变化时，才做最小类型和加载逻辑调整。

## 非目标

- 不做数据库结构迁移。
- 不重写入库确认、出库确认、库存流水写入、销售账单创建等 Store 写流程。
- 不重做 `frontend/src/views/store/index.vue` 的页面设计。
- 不新增日报、补货建议、客户复购提醒等第三阶段智能经营功能。
- 不改变本地运行流程；后端验证仍优先使用编译触发热加载。

## 推荐方案

采用聚焦的读路径治理：

1. 为库存列表、入库单列表、出库单列表和流水列表补充小型查询参数对象，或在现有方法上增加明确参数。
2. 将服务层仍存在的无边界 `findAll()` 替换为支持 `Pageable` 和基础过滤条件的仓储查询。
3. 保持写方法不变；只有读路径治理直接需要时，才提取很小的私有辅助方法。
4. 统一 AI 查询结果表达，让每个 Store 工具返回来源信息、记录数、查询上限、结构化 records 和简洁摘要文本。

这样可以先给第三阶段提供稳定的数据基础，同时把改动范围控制在低风险读路径。

## 后端设计

### Store 库存与单据列表

`StoreInventoryService` 中以下读方法不应继续默认返回无边界列表：

- `listStocks`
- `listInbounds`
- `listOutbounds`
- `listLedgers`

每个读方法应支持当前经营台和后续 AI 查询需要的基础条件：

- `page`
- `size`
- `sku`
- `orderNo`
- `status`
- `startDate`
- `endDate`

服务层需要对 `size` 做上限保护。默认页大小建议为 20，最大页大小建议为 100。这个范围足够经营台使用，也能避免误把大量历史数据一次性推给前端或 AI 工具。

仓储层只补必要查询方法。优先使用 Spring Data `@Query` 搭配可空过滤条件和 `Pageable`，不引入复杂动态查询框架。

### StoreQueryService

`StoreQueryService` 已经通过 `DEFAULT_LIMIT` 和 `MAX_LIMIT` 对 AI 工具查询做了限制，应继续保留这个模式，并把结果契约表达得更明确。

`QueryResult<T>` 应清晰表示：

- `source`：稳定的机器可读来源标识，例如 `store_inventory_stock`。
- `sourceLabel`：中文来源名，例如 `实时库存`。
- `records`：查询得到的结构化记录。
- `content`：供模型或工具展示使用的简洁中文摘要。
- `recordCount`：本次返回记录数。
- `limit`：本次实际使用的查询上限。
- `truncated`：是否可能还有更多匹配记录未返回。

如果为了精确计算 `truncated` 需要额外 count 查询，导致首轮改动过大，可以先保守实现为 `records.size() >= limit`，并在注释中说明这是“可能被截断”的信号。这样至少能避免 AI 回复把有限结果误说成全量结果。

### 隐私边界

`querySalesBills` 必须继续保持严格：

- 允许 `customerPhone` 或 `billNo` 查询。
- 拒绝只提供 `customerName` 的查询。
- 保留现有错误码 `STORE_QUERY_CUSTOMER_PHONE_REQUIRED`。

测试需要证明这条边界没有被整理工作放松。

### 注释要求

只在容易被后续维护破坏的业务规则旁添加简洁注释：

- 页大小上限及其原因。
- 销售账单隐私门槛。
- “可能被截断”的语义。
- 库存列表过滤如何避免整表读取。

不要添加只复述方法名的注释。

## 前端设计

本轮不改变前端视觉结构。

如果后端列表接口从数组改为分页响应，需要最小更新 `frontend/src/service/api/store.ts` 类型，以及经营台页面的数据加载逻辑。页面可以继续使用当前表格布局，但请求时应传递分页参数，而不是依赖后端返回全部记录。

如果首轮实现中直接改变接口响应结构会让范围变大，则先保持 Controller 响应兼容，只在服务端应用有界默认值。显式分页控件可以放到单独的 UI 治理任务中处理。

## 测试设计

后端测试应验证行为，而不是绑定内部实现细节：

- `StoreInventoryServiceTest` 覆盖库存、入库单、出库单和流水列表的有界查询。
- `StoreQueryServiceTest` 覆盖实际查询上限、隐私拒绝、结果元数据和库存预警查询。
- `AgentToolRegistryTest` 覆盖 Store 工具响应仍包含来源名和结构化记录。

实现后的推荐命令：

```bash
mvn -q -Dtest=StoreInventoryServiceTest,StoreQueryServiceTest,AgentToolRegistryTest test
mvn -q -DskipTests compile
```

如果修改了 `store.ts` 或 `store/index.vue`，需要验证前端类型：

```bash
cd frontend && pnpm typecheck
```

运行态验证应在最新代码已经加载到本地运行环境后进行：

- 后端：`http://localhost:8081`
- 前端：`http://localhost:9527`
- 按实际改动范围检查经营台页面或聊天中的 Store 工具查询。

## 验收标准

- Controller 使用的 Store 服务读方法不再默认整表读取。
- AI Store 查询工具仍能返回正确结构化记录和带来源的中文摘要。
- 销售账单查询不能通过客户姓名模糊查询泄露客户历史。
- Store 定向后端测试通过。
- 后端编译通过。
- 如果改动前端文件，前端 typecheck 通过。
- 不把本地配置、IDE 文件、工具目录等无关工作树变化混入本次治理。

## 实施顺序

1. 新增或更新 Store 查询测试，先锁定目标读路径行为。
2. 为库存、单据和流水列表补充有界仓储查询方法。
3. 更新 `StoreInventoryService` 读方法，使用有界过滤查询。
4. 必要时收紧 `StoreQueryService.QueryResult` 元数据。
5. 只有结果结构变化时，才同步更新 `AgentToolRegistry` 映射。
6. 只有接口兼容性要求时，才做最小前端类型和加载逻辑更新。
7. 运行 Store 定向后端测试、后端编译，并在涉及前端时运行 typecheck。
