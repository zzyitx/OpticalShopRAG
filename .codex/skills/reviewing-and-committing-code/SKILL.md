---
name: reviewing-and-committing-code
description: Use when the user asks to commit code or uses trigger phrases such as "提交", "提交代码", "commit", or "commit code".
---

# Reviewing And Committing Code

## Goal

Create an intentional Git commit that is easy to review and understand. Audit comments and formatting before staging, verify the exact change set, and keep unrelated work out of the commit.

## Workflow

1. Read repository instructions and inspect `git status`, unstaged diff, and staged diff.
2. Classify changed files by module and purpose. Preserve user changes and exclude unrelated environment, generated, or experimental files unless they are required.
3. Review the code before committing:
   - Confirm modules and public components have concise responsibility comments where useful.
   - Confirm key functions and non-obvious code blocks explain business rules, side effects, transactions, audit history, permissions, and consistency checks.
   - Confirm persistent entity fields and database structures satisfy repository comment rules.
   - Remove noisy comments that merely restate code.
   - Check formatting, trailing whitespace, and generated-file conventions.
4. Run the strongest relevant verification available: targeted tests, compile/build, typecheck, lint, and `git diff --check`. Report baseline failures separately.
5. Stage explicit paths only. Never use broad staging when unrelated changes exist.
6. Inspect `git diff --cached --stat` and `git diff --cached` before committing. Check for secrets, accidental generated files, and missing required files.
7. Write a commit message that lets a developer understand the change immediately:
   - Subject: `<type>(<module>): <clear outcome>`
   - Body: group bullets by module/function and explain key behavior or logic.
   - Include a `验证:` section listing commands that actually passed.
8. Create the commit, then verify it with `git show --stat --oneline HEAD` and `git status --short`.

## Commit Message Example

```text
feat(store): complete phase-one store operations

- 商品与库存：新增商品档案、出入库确认、库存流水和风险状态
- 销售账单：保留客户配镜历史、修改快照和 CSV 导入
- 前端经营台：接入看板、商品、库存和账单操作
- 审计与规范：记录真实操作人并补齐关键业务注释

验证:
- mvn -q -DskipTests compile
- mvn -q -Dtest=Store*Test test
- pnpm typecheck
```

## Guardrails

- Do not commit secrets, local credentials, IDE-only settings, dependency experiments, or unrelated edits.
- Do not amend, push, or create a pull request unless the user explicitly asks.
- Do not claim a check passed without fresh command output.
- If hooks change files or reject the commit, inspect the result, fix the cause, re-verify, and commit again.
