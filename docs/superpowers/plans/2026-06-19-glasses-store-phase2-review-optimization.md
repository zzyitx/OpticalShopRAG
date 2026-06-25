# 眼镜店阶段二审查后优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧销售账单 AI 查询的隐私边界，并建立可复用、基于真实验证证据的审查后优化报告目录。

**Architecture:** 在 `StoreQueryService` 业务门面统一执行手机号/账单号校验，Repository 和 AI 工具协议保持不变。报告采用纯 Markdown 目录，不增加生成脚本、服务或前端状态处理器。

**Tech Stack:** Java 17、Spring Boot 3.4、JUnit 5、Mockito、Maven、Markdown、Vue 3 TypeScript 类型检查。

---

### Task 1: 销售账单查询隐私约束

**Files:**
- Modify: `src/test/java/com/yizhaoqi/smartpai/service/StoreQueryServiceTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/StoreQueryService.java`

- [x] **Step 1: 写入空筛选查询失败测试**

```java
@Test
void shouldRejectSalesBillQueryWithoutPhoneOrBillNo() {
    CustomException exception = assertThrows(CustomException.class, () -> service.querySalesBills(
            new StoreQueryService.SalesBillQuery(null, null, null, null, null, 10)
    ));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    assertEquals("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", exception.getMessage());
    verifyNoInteractions(salesBillRepository);
}
```

- [x] **Step 2: 运行测试并确认红灯**

Run:

```powershell
$env:JAVA_HOME='D:\openjdk-26.0.1'
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q '-Dtest=StoreQueryServiceTest#shouldRejectSalesBillQueryWithoutPhoneOrBillNo' test
```

Expected: FAIL，因为当前实现会调用 `salesBillRepository.searchBills`，而不是抛出 `CustomException`。

- [x] **Step 3: 实施最小业务修复**

将校验改为：

```java
if (!StringUtils.hasText(customerPhone) && !StringUtils.hasText(billNo)) {
    throw new CustomException("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
}
```

- [x] **Step 4: 运行测试并确认绿灯**

重复 Step 2 命令。Expected: PASS。

- [x] **Step 5: 运行查询与工具链回归测试**

```powershell
$env:JAVA_HOME='D:\openjdk-26.0.1'
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q '-Dtest=StoreQueryServiceTest,AgentToolRegistryTest' test
```

Expected: PASS，0 failures。

### Task 2: 建立审查后优化报告目录

**Files:**
- Create: `docs/code-reviews/post-review-optimizations/README.md`
- Create: `docs/code-reviews/post-review-optimizations/glasses-store-phase2-post-review-optimization-2026-06-19.md`

- [x] **Step 1: 新增目录规范 README**

README 明确每份报告必须包含：关联审查、逐项审核结论、必要性与项目收益、实际改动、验证证据、启动状态、冗余评估、耦合评估和遗留限制。

- [x] **Step 2: 新增本次审查后优化报告**

报告逐项覆盖 C1、W1-W4、I1-I3，说明采纳或不采纳及代码证据；记录 W2 的测试和改动。验证章节只写实际命令结果，不把依赖不可达描述成代码启动失败。

### Task 3: 全量验证与报告收口

**Files:**
- Modify: `docs/code-reviews/post-review-optimizations/glasses-store-phase2-post-review-optimization-2026-06-19.md`

- [x] **Step 1: 编译后端**

```powershell
$env:JAVA_HOME='D:\openjdk-26.0.1'
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: exit code 0。

- [x] **Step 2: 检查前端类型**

```powershell
& C:\Users\28279\AppData\Local\pnpm\bin\pnpm.CMD typecheck
```

Expected: exit code 0。

- [x] **Step 3: 检查格式与变更范围**

```powershell
git -c safe.directory=D:/ideaProject/PaiSmart diff --check
git -c safe.directory=D:/ideaProject/PaiSmart status --short
```

Expected: `diff --check` 无错误；状态中仅增加本计划范围内改动，同时保留实施前已存在的其他工作区改动。

- [x] **Step 4: 检查运行端口和依赖可达性**

检查 `8081`、`9527`、MySQL、Redis、Kafka、Elasticsearch 和 MinIO。只有后端日志出现 `Tomcat started on port 8081` 且端口真实监听时，报告才可写“后端已启动”。

- [x] **Step 5: 回填最终证据**

把测试数量、编译结果、类型检查结果、端口事实、冗余和耦合结论写入本次报告；对无法完成的浏览器验证明确说明阻塞原因。
