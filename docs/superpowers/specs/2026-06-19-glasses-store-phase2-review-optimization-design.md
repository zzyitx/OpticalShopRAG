# 眼镜店阶段二审查后优化设计

## 目标

基于 `docs/code-reviews/glasses-store-phase2-review.md` 逐条验证审查意见，只实施对当前项目有明确收益的修改，并建立可复用的审查后优化报告目录。

## 审查结论

### 本次采纳

- W2 销售账单查询约束：当手机号和账单号均为空时拒绝查询。该修改避免 AI 工具通过空参数或仅凭姓名返回最近账单，缩小客户隐私暴露面。
- 审查后优化报告机制：在 `docs/code-reviews/post-review-optimizations/` 下保存目录说明和逐次报告，记录意见判断、证据、改动与验证结果。

### 本次不采纳

- C1 WebSocket `tool_call` 事件丢失：实际由 `frontend/src/views/chat/modules/input-box.vue` 的 watcher 处理，并已实现事件去重和状态更新。在 chat store 再处理会造成双重消费。
- W1 快照消息初始化 `toolEvents`：现有发送路径已经初始化，展示组件也兼容 `undefined`；仅补空数组不能恢复重连前未持久化的工具事件，收益不足。
- W3 将眼镜店规则注入普通流式路径：门店工具只在 ReAct 工具调用链可用，向无工具路径复制规则不会获得工具能力，反而形成两份提示词规则。
- W4 快捷问题：属于独立体验需求，不是正确性或安全修复，本次不扩大前端范围。
- I1 日期偏移量、I2 单值结果重载、I3 Repository Javadoc：当前实现语义明确；新增常量、重载或逐方法注释的收益不足以抵消代码噪声。

## 代码设计

在 `StoreQueryService.querySalesBills` 的业务入口统一校验：手机号与账单号至少提供一个。`customerName` 仍可作为附加筛选条件，但不能单独触发客户账单查询。Repository 查询保持不变，避免把隐私规则下沉到通用数据访问层。

先在 `StoreQueryServiceTest` 增加失败用例，覆盖空参数查询；确认测试因当前实现允许无条件查询而失败后，再修改生产代码使其通过。保留已有“仅姓名查询被拒绝”和“手机号精确查询成功”用例。

## 报告设计

新增目录：

- `docs/code-reviews/post-review-optimizations/README.md`
- `docs/code-reviews/post-review-optimizations/glasses-store-phase2-post-review-optimization-2026-06-19.md`

README 固定每次报告应包含：原审查报告、逐项结论、必要性与收益、实际改动、验证证据、启动状态、冗余评估、耦合评估和遗留限制。

本次报告在实施和验证完成后填写真实命令结果。环境依赖未启动时不得宣称应用已经完成真实启动，只能分别报告代码可编译、测试通过和运行环境阻塞项。

## 耦合与冗余约束

- 隐私校验保留在 `StoreQueryService`，不修改 Repository 协议。
- 不新增服务、DTO、异常类型或前端事件处理器。
- 不复制 LLM 提示词，不增加单值结果重载。
- 报告模板只定义内容规范，不引入生成脚本或额外工具链。

## 验证方案

1. 定向运行 `StoreQueryServiceTest`，完成失败到通过的 TDD 循环。
2. 运行 `StoreQueryServiceTest,AgentToolRegistryTest`，验证查询服务和工具注册链路。
3. 运行 `mvn -q -DskipTests compile`。
4. 运行前端 `pnpm typecheck`，确认未产生跨端类型回归。
5. 运行 `git diff --check`，检查格式问题。
6. 检查 `8081`、`9527` 和依赖端口；仅在依赖可用且用户现有启动方式允许时进行真实启动验证。
