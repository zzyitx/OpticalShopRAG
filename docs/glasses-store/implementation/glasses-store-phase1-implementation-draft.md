# 眼镜店改造阶段一实施方案稿

> 状态：阶段一收尾实现完成，待真实运行态验收
> 日期：2026-06-07
> 关联文档：`../requirements/glasses-store-rag-prd.md`、`../plans/glasses-store-rag-implementation-plan.md`

## 1. 阶段一目标

阶段一先把 PaiSmart 从通用知识库系统改造成可运行、可验收的眼镜店业务 MVP。核心目标不是一次性完成全部页面和 AI 智能化，而是先建立稳定的业务数据底座、库存闭环、账单闭环，并完成配置脱敏，确保代码可以安全推送到 GitHub。

阶段一完成后应具备：

- 商品档案、单店单仓库存、入库、出库、库存流水的后端闭环。
- 销售账单、客户手机号、左右眼度数字段、历史配镜记录的后端闭环。
- 账单 Excel 模板下载和历史账单导入的最小能力。
- 前端具备可访问的阶段一业务入口和基础 CRUD 页面。
- 当前配置文件中的真实 key、密码、私钥默认值全部移出源码，改为环境变量注入。
- 后端可编译，前端类型检查可运行，浏览器可完成至少一条商品入库、销售出库、账单查询链路验证。

## 2. 范围边界

阶段一只面向单店、单仓、少于 5 人的小型眼镜实体店。继续沿用现有登录、JWT、角色和组织标签基础能力，但业务上不引入多门店、多仓库、调拨、盘点、审批流和真实支付对接。

本阶段包含：

- 配置安全清理。
- 后端领域模型、Repository、Service、Controller、DTO。
- 商品、库存、入库、出库、库存流水。
- 销售账单、账单商品明细、账单修改记录、客户历史配镜查询。
- Excel 模板下载和导入。
- 前端 API 封装、类型声明、路由菜单和最小业务页面。
- 现有 RAG 上传与聊天能力保留，不破坏文档引用和历史渲染路径。

本阶段暂不包含：

- 多门店、多仓库、库存调拨、盘点。
- 入库/出库审批流。
- 扫码枪、条码打印。
- 独立客户档案模块。
- 真实支付、退款、供应商结算。
- 完整 AI 结构化工具编排。阶段一只预留服务接口和数据源，AI 工具接入放在后续批次，避免首批改动过大。

## 3. 实施策略选择

### 方案 A：后端闭环优先，前端做最小可用入口

先完成数据库模型、事务规则、接口和基础页面。优点是数据一致性风险最小，浏览器可以验证真实业务链路；缺点是首批 UI 不会一次性很精美。推荐采用。

### 方案 B：前后端全量同步铺开

同时推进所有页面、仪表盘、AI 工具和业务闭环。优点是视觉完成度高；缺点是改动面积大，容易在库存事务、账单导入、AI 引用路径之间互相拖慢，不适合第一批。

### 方案 C：只做配置安全和数据库模型

先把配置脱敏和实体建完。优点是风险最低；缺点是用户无法在浏览器中验证业务价值，阶段一结果过薄。

推荐选择方案 A：先用后端闭环保证业务正确，再用最小前端页面跑通自然验证路径。配置安全清理作为阶段一第一步执行。

## 4. 安全配置改造

当前风险点集中在 `src/main/resources/application*.yml` 和本地 `.env` 使用方式。阶段一实施时按以下规则处理：

- `application.yml`、`application-dev.yml`、`application-docker.yml` 中的 API key、服务密码、JWT secret、MinIO secret、Elasticsearch password、微信支付密钥默认值改为空默认或非敏感占位。
- 保留 Spring 形式的环境变量注入，例如 `${DEEPSEEK_API_KEY:}`、`${EMBEDDING_API_KEY:}`、`${JWT_SECRET_KEY:}`。
- 不在源码配置里保留真实默认密码、真实 API key 或可用私钥路径默认值。
- 新增或更新 `.env.example`，只放变量名、示例格式和非真实占位值。
- 本地 `.env` 继续被 `.gitignore` 忽略，用于保存个人开发环境真实值。
- 在 README 或阶段一文档中补充“启动前必须复制 `.env.example` 为 `.env` 并填入本地值”的说明。

优先处理的变量：

- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_PASSWORD`
- `ELASTICSEARCH_PASSWORD`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `JWT_SECRET_KEY`
- `ADMIN_BOOTSTRAP_PASSWORD`
- `DEEPSEEK_API_KEY`
- `EMBEDDING_API_KEY`
- `WX_PAY_API_V3_KEY`
- `WX_PAY_PRIVATE_KEY`

验收方式：

- 全仓搜索不再出现真实 API key 字符串或本地服务密码默认值。
- `.env` 不进入 Git 追踪。
- `mvn -q -DskipTests compile` 能读取环境变量占位配置并通过编译。

## 5. 后端设计

新增包建议继续放在现有分层下，不额外引入复杂模块边界：

- `model`：JPA 实体。
- `repository`：Spring Data JPA Repository。
- `service`：业务事务和校验。
- `controller`：REST 接口。
- `model.dto` 或现有 DTO 习惯位置：请求和响应对象。

### 5.1 商品模型

实体建议：`StoreProduct`

核心字段：

- `id`
- `sku`
- `name`
- `category`
- `brand`
- `model`
- `barcode`
- `specification`
- `color`
- `material`
- `unit`
- `purchasePrice`
- `retailPrice`
- `supplier`
- `safeStock`
- `status`
- 镜框扩展字段：框型、尺寸、鼻梁宽度、镜腿长度、适配镜片类型。
- 镜片扩展字段：折射率、球镜范围、散光范围、功能类型、镀膜类型。
- 审计字段：创建人、创建时间、更新人、更新时间。

规则：

- `sku` 唯一且必填。
- `barcode` 可为空，阶段一不做扫码。
- 下架或停用商品不能新增入库/出库明细，但历史单据保留。

### 5.2 库存模型

实体建议：

- `StoreInventoryStock`
- `StoreInboundOrder`
- `StoreInboundItem`
- `StoreOutboundOrder`
- `StoreOutboundItem`
- `StoreInventoryLedger`

规则：

- 阶段一固定单仓，可用一个默认仓库编码，例如 `DEFAULT`，不做仓库管理页面。
- 入库单、出库单支持 `DRAFT`、`CONFIRMED`、`CANCELLED`。
- 确认入库在同一事务中增加库存并写库存流水。
- 确认出库在同一事务中扣减库存并写库存流水。
- 出库数量不得超过当前库存。
- 已确认单据不允许物理删除，不允许重复确认。
- 库存流水记录变更前数量、变更数量、变更后数量、业务单号、操作人和操作来源。

### 5.3 账单模型

实体建议：

- `StoreSalesBill`
- `StoreSalesBillItem`
- `StoreSalesBillChangeLog`

核心字段：

- 账单号。
- 客户姓名。
- 客户手机号，必填。
- 购买日期。
- 左眼近视、左眼散光、左眼轴位。
- 右眼近视、右眼散光、右眼轴位。
- 瞳距。
- 镜框型号、镜片型号。
- 支付金额、优惠金额、实收金额、支付方式。
- 销售员、验光师、备注。

规则：

- 同一手机号多次配镜生成多条账单记录，不覆盖历史。
- 度数字段在数据库中拆分存储，响应 DTO 中额外提供组合展示字符串。
- 修改账单必须写入变更记录。
- 创建账单时可选择自动生成销售出库单并扣减库存。

## 6. 接口设计

接口统一放在 `/api/v1/store/**` 下。

商品：

- `GET /api/v1/store/products`
- `POST /api/v1/store/products`
- `GET /api/v1/store/products/{id}`
- `PUT /api/v1/store/products/{id}`
- `PATCH /api/v1/store/products/{id}/status`

库存：

- `GET /api/v1/store/inventory/stocks`
- `GET /api/v1/store/inventory/low-stock`
- `POST /api/v1/store/inventory/inbound`
- `GET /api/v1/store/inventory/inbound`
- `POST /api/v1/store/inventory/inbound/{id}/confirm`
- `POST /api/v1/store/inventory/inbound/{id}/cancel`
- `POST /api/v1/store/inventory/outbound`
- `GET /api/v1/store/inventory/outbound`
- `POST /api/v1/store/inventory/outbound/{id}/confirm`
- `POST /api/v1/store/inventory/outbound/{id}/cancel`
- `GET /api/v1/store/inventory/ledgers`

账单：

- `GET /api/v1/store/sales-bills`
- `POST /api/v1/store/sales-bills`
- `GET /api/v1/store/sales-bills/{id}`
- `PUT /api/v1/store/sales-bills/{id}`
- `GET /api/v1/store/sales-bills/customer-history?phone=...`
- `GET /api/v1/store/sales-bills/{id}/changes`
- `GET /api/v1/store/sales-bills/template`
- `POST /api/v1/store/sales-bills/import`

仪表盘最小接口：

- `GET /api/v1/store/dashboard/summary`

## 7. 前端设计

阶段一前端以“可验证业务链路”为准，不做营销式首页，不引入复杂视觉重构。

新增内容：

- `frontend/src/service/api/store.ts`
- `frontend/src/typings/api.d.ts` 中新增 `Api.Store` 类型。
- 路由和菜单新增：
  - `store-dashboard`
  - `store-product`
  - `store-inventory-stock`
  - `store-inbound`
  - `store-outbound`
  - `store-inventory-ledger`
  - `store-sales-bill`

页面要求：

- 使用现有 Naive UI 表格、抽屉表单、弹窗确认和消息提示风格。
- 商品页支持列表、新增、编辑、启停用。
- 库存页支持库存列表、低库存筛选。
- 入库/出库页支持创建草稿、确认、取消。
- 账单页支持新增、编辑、查看历史配镜记录、下载模板、导入。
- 仪表盘展示今日销售额、低库存数量、今日入库、今日出库、草稿单据数量。

## 8. 数据迁移与表结构

项目当前主要依赖 JPA/Hibernate 自动建表，阶段一先沿用现有模式，减少引入 Flyway/Liquibase 的额外成本。

实施注意：

- 实体表名使用清晰前缀，例如 `store_product`、`store_inventory_stock`、`store_sales_bill`。
- 金额使用 `BigDecimal`。
- 数量使用整数或 `BigDecimal`，若商品单位存在“盒、瓶、片、个”，阶段一按整数库存处理。
- 日期时间使用 `LocalDate`、`LocalDateTime`。
- 关键枚举在 Java 层定义，数据库中保存字符串。

如果实施中发现当前环境不适合自动建表，再补充 SQL 初始化脚本，但不在第一步强制引入迁移框架。

## 9. AI 与 RAG 处理

阶段一保留现有 RAG 链路和聊天体验，不破坏：

- 文档上传。
- MinIO 文件存储。
- Kafka 异步处理。
- 文档解析。
- 向量化。
- Elasticsearch 检索。
- 聊天引用映射和历史渲染。

阶段一后端业务数据要为 AI 查询预留服务方法，例如：

- 查询商品。
- 查询实时库存。
- 查询低库存。
- 查询客户历史配镜记录。
- 查询销售统计。

但 `AgentToolRegistry` 新增完整工具和提示词重写建议放在阶段一后半批次，等业务数据和页面验证稳定后再接入。这样可以避免 AI 工具调试掩盖库存事务和账单数据问题。

## 10. 实施顺序

1. 配置安全清理：移除源码中的真实默认 key 和密码，补齐 `.env.example`。
2. 后端商品模型、Repository、DTO、Service、Controller。
3. 后端库存模型、入库/出库事务、库存流水。
4. 后端账单模型、账单明细、修改记录、客户历史查询。
5. Excel 模板下载和账单导入。
6. 后端仪表盘 summary。
7. 前端 `store.ts` API 封装和 `Api.Store` 类型。
8. 前端路由、菜单和最小页面。
9. 浏览器端到端验证。
10. 根据验证结果修补字段、错误提示和边界条件。

## 11. 测试与验收

后端验证：

- `mvn -q -DskipTests compile`
- 商品 SKU 唯一校验。
- 入库确认后库存增加并写流水。
- 出库确认后库存减少并写流水。
- 库存不足时出库失败且不写流水。
- 已确认或已取消单据不能重复确认。
- 同一手机号可生成多条配镜账单。
- 账单修改会生成修改记录。
- Excel 导入缺少手机号、日期错误、金额错误时返回行号和原因。

前端验证：

- `cd frontend && pnpm typecheck`
- 浏览器访问 `http://localhost:9527`。
- 商品新增、编辑、启停用可用。
- 入库创建并确认后库存列表更新。
- 出库创建并确认后库存列表更新。
- 账单新增后客户历史配镜记录可查。
- 模板可下载，导入错误可展示。

安全验证：

- 搜索源码不再出现真实 API key。
- 搜索源码不再出现本地服务密码作为默认值。
- `.env` 仍被 `.gitignore` 忽略。
- `.env.example` 不包含真实密钥。

## 12. 风险与处理

- 现有文档中中文在部分终端显示为乱码：实施时以文件内容和浏览器/编辑器显示为准，不把乱码复制进新代码。
- JPA 自动建表可能和本地已有库结构产生差异：实施前先查看当前 `spring.jpa.hibernate.ddl-auto`，必要时只新增表，不改旧表。
- 前端路由类型由项目生成文件维护：新增页面时按现有 elegant-router 约定更新，避免手工改错生成类型。
- 配置脱敏后本地启动依赖 `.env`：需要保留本地 `.env`，并在文档说明必填变量。
- AI 工具暂缓接入可能让“自然语言查库存”不是第一批验收重点：第一批先验收结构化接口和页面，AI 接入作为后续可控增量。

## 13. 审阅结论

建议按本方案进入实施。实施前需要确认一件事：阶段一第一批是否按“后端闭环 + 最小前端 + 配置安全”推进，AI 结构化工具接入放到业务页面验证稳定之后。
