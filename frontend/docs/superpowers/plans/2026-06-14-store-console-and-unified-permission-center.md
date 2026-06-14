# Store Console and Unified Permission Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the store console, fix authenticated Excel template downloads, and add an admin-only unified role and permission center covering store and RAG operations.

**Architecture:** Keep RAG organization tags as the data-scope layer and add an operation-level RBAC layer for functional authorization. The backend computes effective permissions with an unconditional ADMIN bypass; the frontend consumes effective permission codes for menu and button visibility while all security-sensitive decisions remain enforced by backend APIs.

**Tech Stack:** Vue 3, TypeScript, Pinia, Naive UI, Elegant Router, Axios, Spring Boot, Spring Security, Spring Data JPA, JUnit 5, Mockito.

---

### Task 1: Authenticated Excel Template Download

**Files:**
- Modify: `frontend/src/service/api/store.ts`
- Modify: `frontend/src/views/store/index.vue`
- Verify: `frontend/package.json`

- [ ] Add `fetchStoreSalesBillTemplate()` using the existing authenticated request instance with `responseType: 'blob'`.
- [ ] Replace `window.open` with a Blob download helper that creates and revokes an object URL.
- [ ] Run `pnpm typecheck`; verify the new request response type and browser APIs compile.

### Task 2: Store Console Layout and Existing-Style Alignment

**Files:**
- Modify: `frontend/src/views/store/index.vue`

- [ ] Preserve all existing store operations and current user changes.
- [ ] Replace the page-level `NSpace` layout with the existing page container convention used by user management.
- [ ] Keep the overview cards at the top and organize product, inventory, inbound, outbound, sales bill, and customer history areas under `NTabs`.
- [ ] Use existing `NCard`, `NDataTable`, forms, buttons, spacing utilities, responsive grids, and dark-mode behavior only.
- [ ] Run `pnpm typecheck` and `pnpm build`.

### Task 3: Backend Permission Domain and Effective Permission Resolution

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/model/Permission.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/BusinessRole.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/RolePermission.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/UserBusinessRole.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/UserPermissionOverride.java`
- Create: corresponding repositories under `src/main/java/com/yizhaoqi/smartpai/repository/`
- Create: `src/main/java/com/yizhaoqi/smartpai/service/PermissionService.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/PermissionServiceTest.java`

- [ ] Write failing service tests proving ADMIN always has every permission.
- [ ] Write failing tests proving effective permissions equal role grants plus user grants minus explicit denies.
- [ ] Implement focused JPA entities and repositories with unique constraints.
- [ ] Implement `PermissionService` and make explicit deny take precedence.
- [ ] Run `mvn -q -Dtest=PermissionServiceTest test`.

### Task 4: Permission Catalog and Existing-User Migration

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializer.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializerTest.java`

- [ ] Write a failing idempotency test for catalog and default-role initialization.
- [ ] Seed stable permission codes for store, RAG, and system administration.
- [ ] Create a protected default RAG user role that preserves current ordinary-user capabilities.
- [ ] Assign existing USER accounts to the default role without adding store-management permissions.
- [ ] Run the initializer test twice and verify no duplicate rows or assignments.

### Task 5: Backend Permission APIs and Enforcement

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/controller/PermissionAdminController.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/controller/UserController.java`
- Modify: store controllers under `src/main/java/com/yizhaoqi/smartpai/controller/`
- Modify: `src/main/java/com/yizhaoqi/smartpai/config/SecurityConfig.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/controller/PermissionAdminControllerTest.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/config/StorePermissionAuthorizationTest.java`

- [ ] Write failing tests for admin-only role, catalog, and user-authorization APIs.
- [ ] Write failing authorization tests for store view, create, update, import, and template-download permissions.
- [ ] Add `/api/v1/admin/permissions/**` APIs for catalog, roles, role permissions, user roles, user grants, and user denies.
- [ ] Return effective permission codes from `/api/v1/users/me`.
- [ ] Enforce store permissions using one reusable backend authorization mechanism.
- [ ] Keep ADMIN unconditionally allowed and return HTTP 403 for denied authenticated users.
- [ ] Run focused controller and authorization tests.

### Task 6: Frontend Permission State and APIs

**Files:**
- Modify: `frontend/src/typings/api.d.ts`
- Modify: `frontend/src/store/modules/auth/index.ts`
- Create: `frontend/src/service/api/permission.ts`
- Modify: `frontend/src/service/api/index.ts`

- [ ] Add `permissions: string[]` to user info with a safe empty default.
- [ ] Add `hasPermission(code)` that always returns true for ADMIN.
- [ ] Add typed permission catalog, role, and user-authorization API clients.
- [ ] Run `pnpm typecheck`.

### Task 7: Admin Permission Center Page

**Files:**
- Create: `frontend/src/views/permission-center/index.vue`
- Create: focused modules under `frontend/src/views/permission-center/modules/`
- Modify generated Elegant Router route/import/type files required for a new `permission-center` route.
- Modify locale route labels under `frontend/src/locales/langs/`.

- [ ] Add an ADMIN-only permission-center route beside user management.
- [ ] Build Role Management, User Authorization, and Permission Catalog tabs using the existing user-management page style.
- [ ] Keep the permission catalog read-only.
- [ ] Allow role assignment, user grants, user denies, and existing organization-tag configuration from the user authorization tab.
- [ ] Display the explicit-deny precedence rule and effective-permission summary.
- [ ] Run `pnpm typecheck` and `pnpm build`.

### Task 8: Frontend Store Permission Integration

**Files:**
- Modify: `frontend/src/views/store/index.vue`
- Modify: `frontend/src/router/elegant/routes.ts`

- [ ] Show store tabs only when the user has the corresponding view permission.
- [ ] Show create, update, import, and template-download controls only with matching operation permissions.
- [ ] Preserve full visibility and behavior for ADMIN.
- [ ] Run `pnpm typecheck` and `pnpm build`.

### Task 9: Full Verification

**Files:**
- Verify all modified frontend and backend files.

- [ ] Run `mvn -q -DskipTests compile`.
- [ ] Run focused permission and store tests.
- [ ] Run `pnpm typecheck`.
- [ ] Run `pnpm build`.
- [ ] Run `git diff --check`.
- [ ] Review the final diff to ensure unrelated existing changes were not reverted or staged.
