# Store Read Path Cleanup Design

## Context

PaiSmart has merged the phase-two structured store query work into `main`. The Store business chain now includes product master data, inventory, inbound and outbound orders, inventory ledger, sales bills, dashboard stats, and AI-facing read-only tools.

The next cleanup should improve the Store read path before adding phase-three intelligent operation features. Current code already has clear write-side transaction boundaries for inventory and sales bills, so this design intentionally avoids changing stock mutation, bill creation, or payment-related behavior.

## Goals

- Remove risky unbounded Store read paths, especially list methods that still load all rows.
- Keep AI store tools bounded, source-aware, and easy to validate from tests.
- Preserve the sales-bill privacy boundary: sales history queries must require `customerPhone` or `billNo`.
- Improve method clarity without broad refactors or new infrastructure.
- Leave the frontend visual structure unchanged unless a backend response shape requires a small type update.

## Non-Goals

- No database schema migration.
- No rewrite of Store write workflows such as inbound confirmation, outbound confirmation, inventory ledger writes, or sales-bill creation.
- No redesign of `frontend/src/views/store/index.vue`.
- No new phase-three analytics features such as daily reports, replenishment suggestions, or customer repurchase reminders.
- No runtime restart workflow change; backend validation should still prefer compile-driven hot reload when applicable.

## Recommended Approach

Use a focused read-path cleanup:

1. Add small query request objects or method parameters for Store inventory lists, inbound orders, outbound orders, and ledgers.
2. Replace service-level unbounded `findAll()` calls with repository queries that accept `Pageable` and basic filters.
3. Keep existing write methods untouched, except for extracting tiny private helpers only when they directly support the read cleanup.
4. Normalize AI-facing query results so every Store tool response exposes source metadata, record count, limit information, structured records, and a concise text summary.

This gives phase three a safer data foundation while keeping the blast radius narrow.

## Backend Design

### Store Inventory Lists

`StoreInventoryService` should stop returning unbounded lists for these read methods:

- `listStocks`
- `listInbounds`
- `listOutbounds`
- `listLedgers`

Each read method should accept a small query object or explicit parameters that cover the current UI needs:

- `page`
- `size`
- `sku`
- `orderNo`
- `status`
- `startDate`
- `endDate`

The service should clamp `size` to a conservative maximum. A default page size of 20 and a maximum of 100 are enough for the management UI and keep accidental large reads under control.

Repository additions should stay simple and readable. Use Spring Data `@Query` with nullable filters and `Pageable` where existing derived methods are not enough.

### Store Query Service

`StoreQueryService` already bounds AI tool queries with `DEFAULT_LIMIT` and `MAX_LIMIT`. Keep that pattern, but make the result contract more explicit.

The `QueryResult<T>` record should clearly represent:

- `source`: stable machine-readable source key such as `store_inventory_stock`.
- `sourceLabel`: Chinese display label such as `实时库存`.
- `records`: structured records returned by the query.
- `content`: concise text prepared for model/tool display.
- `recordCount`: number of records returned.
- `limit`: effective limit used for the query.
- `truncated`: whether the query may have more matching records than returned.

If adding `truncated` requires an extra count query that would make this pass too large, implement it conservatively as `records.size() >= limit` and document it as a "may be truncated" signal. That is enough for AI responses to avoid implying exhaustive results.

### Privacy Boundary

Keep `querySalesBills` strict:

- Accept `customerPhone` or `billNo`.
- Reject a query that only provides `customerName`.
- Keep the existing error code `STORE_QUERY_CUSTOMER_PHONE_REQUIRED`.

Tests should prove this behavior remains unchanged.

### Comments

Comments should be added only around business rules that are easy to break during future maintenance:

- page-size clamping and why it exists,
- sales-bill privacy gate,
- "may be truncated" semantics,
- inventory list filtering that prevents all-row reads.

Do not add comments that restate method names.

## Frontend Design

The frontend should remain visually unchanged in this pass.

If backend list endpoints change from arrays to paged responses, update `frontend/src/service/api/store.ts` types and the Store page data loading code minimally. The page can continue using the current table layout, but it should pass pagination parameters instead of relying on all records being returned.

If changing the endpoint response shape would make the first implementation too large, keep existing controller responses compatible and apply bounded defaults server-side first. A separate UI cleanup can expose explicit pagination controls.

## Testing Design

Backend tests should focus on behavior, not internal implementation:

- `StoreInventoryServiceTest` should cover bounded stock, inbound, outbound, and ledger listing.
- `StoreQueryServiceTest` should cover effective limit, privacy rejection, result metadata, and inventory warning queries.
- `AgentToolRegistryTest` should cover that Store tool responses still include source labels and structured records.

Recommended commands after implementation:

```bash
mvn -q -Dtest=StoreInventoryServiceTest,StoreQueryServiceTest,AgentToolRegistryTest test
mvn -q -DskipTests compile
```

Frontend validation if `store.ts` or `store/index.vue` changes:

```bash
cd frontend && pnpm typecheck
```

Runtime validation should happen only after code changes are loaded into the active local runtime:

- backend on `http://localhost:8081`,
- frontend on `http://localhost:9527`,
- browser check for the Store page or chat tool query when relevant.

## Acceptance Criteria

- No Store service read method used by controllers loads an unbounded full table by default.
- AI Store query tools still return correct structured records and readable source-aware summaries.
- Sales bill queries cannot expose customer history through name-only fuzzy search.
- Store targeted backend tests pass.
- Backend compile passes.
- Frontend typecheck passes if frontend files were touched.
- Existing local configuration and unrelated worktree changes are not bundled into this cleanup.

## Implementation Order

1. Add or update Store query tests to lock the desired read-path behavior.
2. Add bounded repository query methods for inventory, order, and ledger lists.
3. Update `StoreInventoryService` read methods to use bounded filters.
4. Tighten `StoreQueryService.QueryResult` metadata if needed.
5. Update `AgentToolRegistry` mapping only if the result shape changes.
6. Make minimal frontend type/loading updates only if endpoint compatibility requires it.
7. Run targeted backend tests, backend compile, and frontend typecheck when applicable.
