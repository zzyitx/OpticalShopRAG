# 眼镜店阶段二审查后优化报告

> 优化日期：2026-06-19
> 原审查报告：`docs/code-reviews/glasses-store-phase2-review.md`
> 设计说明：`docs/superpowers/specs/2026-06-19-glasses-store-phase2-review-optimization-design.md`
> 实施计划：`docs/superpowers/plans/2026-06-19-glasses-store-phase2-review-optimization.md`

## 1. 本次范围

本次没有按严重级别机械实施全部意见，而是核对实际调用链、业务边界、测试和运行环境后，只修复已证实的销售账单隐私约束，并建立统一的审查后优化报告目录。

## 2. 审查意见审核

| 编号 | 结论 | 是否有益 | 审核证据与理由 |
| --- | --- | --- | --- |
| C1 `tool_call` 事件丢失 | 不采纳 | 否 | `frontend/src/views/chat/modules/input-box.vue` 已监听 `wsData`，处理 `tool_call`，按 `toolCallId` 去重并更新 `toolEvents`。在 chat store 再处理会双重消费同一 WebSocket 消息并增加重复事件风险。 |
| W1 快照消息初始化 `toolEvents` | 不采纳 | 当前收益不足 | 新发送的 assistant 消息已经初始化 `toolEvents: []`，展示组件也通过 `props.msg.toolEvents || []` 兼容历史消息。只增加空数组不能恢复未持久化的历史工具事件。 |
| W2 销售账单无约束查询 | 采纳 | 是 | 原条件仅拒绝“只有姓名”的查询，所有筛选均为空时会查询最近账单。该行为会向 AI 返回与问题无关的客户信息，应在业务门面阻断。 |
| W3 普通流式路径注入门店规则 | 不采纳 | 否 | 门店工具注册在 ReAct 工具调用链。普通 `buildMessages` 路径没有工具能力，复制规则不会使其获得工具调用能力，只会形成两份提示词并产生漂移。 |
| W4 快捷问题 | 延期 | 有体验收益但非本次必要项 | 这是独立的 UI 引导需求，不影响结构化查询正确性、安全性或启动。本次不扩大前端行为范围。 |
| I1 日期偏移可读性 | 不采纳 | 收益不足 | `DEFAULT_DATE_DAYS - 1L` 和 `MAX_DATE_DAYS - 1L` 表达包含首尾日期的计算规则；再增加常量会把简单公式拆散。 |
| I2 单值结果重载 | 不采纳 | 否 | `QueryResult<T>` 对五类工具保持统一列表协议。为统计单独增加重载会扩大 API 表面并增加分支处理。 |
| I3 Repository Javadoc | 不采纳 | 收益不足 | JPQL、参数名和方法名已经直接表达筛选规则；仓库约定要求关键业务逻辑注释，并未要求每个 Repository 方法都增加 Javadoc。批量补注释会增加重复描述。 |

## 3. 实际改动

### 3.1 业务修复

`StoreQueryService.querySalesBills` 现在要求手机号或账单号至少提供一个：

```java
if (!StringUtils.hasText(customerPhone) && !StringUtils.hasText(billNo)) {
    throw new CustomException("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
}
```

客户姓名仍可与手机号或账单号组合筛选，但不能单独查询，也不能通过全空参数取得最近账单。校验保留在业务查询门面，Repository 协议和 Agent 工具参数结构均未改变。

### 3.2 回归测试

新增 `shouldRejectSalesBillQueryWithoutPhoneOrBillNo`：

- 修复前：测试失败，实际没有抛出 `CustomException`。
- 修复后：测试通过，并验证拒绝发生在 Repository 调用之前。
- 保留已有“仅姓名查询被拒绝”和“手机号精确查询成功”测试。

### 3.3 报告机制

新增 `docs/code-reviews/post-review-optimizations/README.md`，统一后续报告的路径、命名、必填证据和结论规则。

## 4. 验证结果

| 验证项 | 结果 | 证据 |
| --- | --- | --- |
| TDD 红灯 | 符合预期 | 单测报告 `Expected CustomException to be thrown, but nothing was thrown`。 |
| TDD 绿灯 | 通过 | 同一定向测试修改后退出码 0。 |
| 查询与工具链回归 | 通过 | `StoreQueryServiceTest` 7 个、`AgentToolRegistryTest` 2 个，共 9 个测试；0 failures、0 errors。 |
| 完整后端测试 | 未全部通过 | `mvn test` 共运行 103 个测试，3 个失败；失败集中在 `ParseServiceUnitTest` 1 个和 `UploadServiceTest` 2 个，与本次账单查询路径无调用关系。 |
| 后端编译 | 通过 | `mvn -q -DskipTests compile` 退出码 0。 |
| 前端类型检查 | 通过 | `pnpm typecheck` 退出码 0。 |
| MySQL | 可达 | `127.0.0.1:3306` 正在监听。 |
| 后端持续运行 | 未通过，环境阻塞 | 启动日志先出现 `Tomcat started on port 8081`，随后 Elasticsearch 初始化超时，ApplicationContext 失败并关闭，最终 `8081` 未监听。 |
| 前端与浏览器验收 | 未执行 | `9527` 未监听，无法进行真实浏览器和网络请求验证。 |

Maven 在 JDK 26 下仍输出 Guice `Unsafe`、反射 final-field mutation 和 Mockito 动态 agent 警告；这些是现有构建工具兼容性提示，不是本次改动引入的测试失败。

完整测试中的 3 个失败已通过单独运行 `ParseServiceUnitTest,UploadServiceTest` 稳定复现：

- `ParseServiceUnitTest.testBuildLiteParseCommand_UsesJsonOutputAndOcrOptions` 在 Windows 上用 `Path.of("/tmp/output.json")` 构造路径，却断言命令包含 Unix 字符串 `/tmp/output.json`，存在平台相关假设。
- `UploadServiceTest.uploadChunkSkipsDatabaseWhenRedisBitmapHit` 和 `uploadChunkBackfillsRedisWhenDatabaseHasChunkAfterRedisMiss` 仍断言 Repository 或 MinIO 零交互；当前 `UploadService` 的明确规则是由数据库元数据与 MinIO 对象共同确认分片可用于合并，因此测试期望与实现语义不一致。

上述四个 Parse/Upload 源码与测试文件均无工作区改动。由于没有捕获本次实施前的完整测试基线，本报告不把它们断言为“已确认的历史失败”；只能确认它们不经过本次修改的 `StoreQueryService` 路径，且需要单独评审测试跨平台性和上传幂等规则。

## 5. 启动能力判断

本次优化后的代码可以编译，Spring Boot 也完成了 Bean 装配并启动 Tomcat，说明新增校验没有破坏应用启动链路。项目当前不能持续启动，直接原因是 `.env` 指向的 `192.168.65.101:9200` 不可达，`EsIndexInitializer` 抛出 `ConnectException` 后触发应用关闭。

同一 VM 的 `22`、`6379`、`9092`、`9200`、`9000`、`9001` 均不可达；Kafka 管理客户端在启动期间也记录了 `192.168.65.101:9092` 连接警告。恢复 VM 和依赖服务后，仍需重新确认 `8081` 持续监听、`9527` 前端运行以及浏览器端结构化查询链路。

## 6. 冗余评估

本次生产代码只调整一个已有条件，没有新增服务、DTO、异常类型、Repository 方法、前端 watcher 或提示词副本。

明确拒绝 C1、W3 和 I2，分别避免了：

- 同一 WebSocket 事件被两个组件重复处理。
- ReAct 与普通流式路径维护两份门店业务规则。
- 为单个统计结果增加无实际调用收益的重载。

新增 Markdown 目录属于审查可追溯性资料，不参与运行时，也未引入生成脚本或工具依赖。

## 7. 耦合评估

隐私约束位于 `StoreQueryService`，依赖方向仍为 `AgentToolRegistry -> StoreQueryService -> StoreSalesBillRepository`。修改没有让 Repository 感知 AI 规则，也没有让前端承担后端隐私校验。

因此本次没有增加跨层耦合。现有 `AgentToolRegistry` 集中注册多类工具，文件规模已增长，但本次不以一次隐私修复为由拆分注册体系；在工具数量继续明显增长或出现独立生命周期前提前拆分，反而会增加装配复杂度。

## 8. 遗留限制

- 当前 VM 依赖不可达，无法证明恢复依赖后的持续启动和完整端到端行为。
- 完整 Maven 测试仍有 3 个与本次查询修复无关的稳定失败，仓库整体测试状态不能标记为全绿。
- `toolEvents` 未持久化，重连后只能恢复生成快照，不能恢复历史工具执行过程；这与 W1 的“初始化空数组”不是同一问题。
- 快捷问题属于后续可独立评审的体验改进，不应混入本次安全修复。
