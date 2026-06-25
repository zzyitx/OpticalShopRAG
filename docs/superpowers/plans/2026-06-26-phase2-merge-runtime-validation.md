# Phase 2 Merge And Runtime Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the stage-two AI structured store-query work into `main`, repair the known backend test baseline, and verify the main workspace is ready for stage-two runtime acceptance.

**Architecture:** Keep the merge scoped to the existing `codex-phase2-ai-query` commit, then fix only defects proven by the failing Parse/Upload tests. Runtime validation stays evidence-driven: compile/test/typecheck first, then confirm backend/frontend ports and HTTP behavior without restarting the IDE backend unless runtime evidence requires it.

**Tech Stack:** Spring Boot, JUnit/Mockito, Vue 3, TypeScript, pnpm, MySQL/Redis/Elasticsearch/Kafka/MinIO local runtime checks.

---

### Task 1: Controlled Stage-Two Merge

**Files:**
- Modify: files changed by commit `175e1034f1ef1afe2c8bddc33c65ab5cd1b97910`
- Preserve: `SmartPAI.iml`, `frontend/.vscode/*`, `.codex-tmp/`, `.understand-anything/`, `OpticalShopRAG.iml`, `docs/code-reviews/project-comprehensive-review-2026-06-26.md`

- [ ] **Step 1: Confirm dirty files do not overlap with merge files**

Run:

```powershell
git -c safe.directory=D:/ideaProject/PaiSmart diff --name-only
git -c safe.directory=D:/ideaProject/PaiSmart diff --name-only main codex-phase2-ai-query
```

Expected: no overlapping paths except intended stage-two files.

- [ ] **Step 2: Merge without committing**

Run:

```powershell
git -c safe.directory=D:/ideaProject/PaiSmart merge --no-ff --no-commit codex-phase2-ai-query
```

Expected: merge applies cleanly and leaves staged stage-two changes for review.

- [ ] **Step 3: Verify merge scope**

Run:

```powershell
git -c safe.directory=D:/ideaProject/PaiSmart status --short
git -c safe.directory=D:/ideaProject/PaiSmart diff --cached --name-only
```

Expected: staged files are exactly the stage-two commit files; existing local IDE/tool changes remain unstaged or untracked.

### Task 2: Reproduce Backend Baseline Failures

**Files:**
- Test: `src/test/java/com/yizhaoqi/smartpai/service/ParseServiceUnitTest.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/service/UploadServiceTest.java`

- [ ] **Step 1: Run the known failing tests**

Run:

```powershell
mvn -q -Dtest=ParseServiceUnitTest#testBuildLiteParseCommand_UsesJsonOutputAndOcrOptions,UploadServiceTest#uploadChunkBackfillsRedisWhenDatabaseHasChunkAfterRedisMiss,UploadServiceTest#uploadChunkSkipsDatabaseWhenRedisBitmapHit test
```

Expected: the failures reproduce or prove the report is stale.

- [ ] **Step 2: Read the exact failure output**

Use the Maven output and Surefire reports under `target/surefire-reports/` to identify the assertion or mock interaction that fails.

Expected: one concrete root cause for each failure before editing production or test code.

### Task 3: Repair Parse/Upload Test Baseline With Root-Cause Fixes

**Files:**
- Modify only after reproduction identifies the root cause:
  `src/main/java/com/yizhaoqi/smartpai/service/ParseService.java`,
  `src/main/java/com/yizhaoqi/smartpai/service/UploadService.java`,
  or the matching test files if the production behavior is correct and the test expectation is stale.

- [ ] **Step 1: Add or adjust the smallest failing test expectation**

Run the relevant single test after the adjustment.

Expected: RED state is verified if a new assertion is added; stale expectations are changed only when production behavior matches the documented business rule.

- [ ] **Step 2: Implement the minimal code change**

Keep the fix in the failing service or the failing test only. Do not refactor unrelated upload, parsing, or store code.

- [ ] **Step 3: Verify the targeted tests**

Run:

```powershell
mvn -q -Dtest=ParseServiceUnitTest,UploadServiceTest test
```

Expected: both test classes pass.

### Task 4: Stage-Two Main Workspace Verification

**Files:**
- Verify backend and frontend after merge and baseline repair.

- [ ] **Step 1: Compile backend**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: exit code 0.

- [ ] **Step 2: Run stage-two targeted tests**

Run:

```powershell
mvn -q -Dtest=Store*Test,AgentToolRegistryTest test
```

Expected: exit code 0.

- [ ] **Step 3: Run full backend tests**

Run:

```powershell
mvn test
```

Expected: exit code 0 or a documented remaining failure with root cause and owner.

- [ ] **Step 4: Run frontend typecheck**

Run:

```powershell
cd frontend
pnpm typecheck
```

Expected: exit code 0.

- [ ] **Step 5: Check whitespace and merge hygiene**

Run:

```powershell
git -c safe.directory=D:/ideaProject/PaiSmart diff --check
git -c safe.directory=D:/ideaProject/PaiSmart status --short --branch
```

Expected: no whitespace errors; only intended stage-two/fix changes are staged or modified.

### Task 5: Runtime Acceptance Snapshot

**Files:**
- No code changes unless runtime evidence proves a local configuration defect.

- [ ] **Step 1: Confirm service ports**

Run:

```powershell
Test-NetConnection -ComputerName localhost -Port 8081 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 9527 -InformationLevel Quiet
Test-NetConnection -ComputerName 192.168.65.101 -Port 6379 -InformationLevel Quiet
Test-NetConnection -ComputerName 192.168.65.101 -Port 9200 -InformationLevel Quiet
Test-NetConnection -ComputerName 192.168.65.101 -Port 9092 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 9000 -InformationLevel Quiet
```

Expected: `8081`, `9527`, Redis, Elasticsearch, and Kafka are reachable; MinIO status is recorded honestly.

- [ ] **Step 2: Confirm frontend HTTP response**

Run:

```powershell
Invoke-WebRequest -Uri 'http://localhost:9527' -UseBasicParsing -TimeoutSec 5
```

Expected: HTTP 200.

- [ ] **Step 3: Confirm backend is responding**

Run a protected or public backend endpoint and record the HTTP status. A `403` from a protected endpoint counts as "responding but unauthorized", not as a health pass.

Expected: the final report distinguishes listener readiness from authenticated business validation.
