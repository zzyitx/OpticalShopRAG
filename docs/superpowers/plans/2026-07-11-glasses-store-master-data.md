# 眼镜店基础资料 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可维护的计量单位、两级商品类别、统一往来单位、单默认仓库和自动 SKU 商品档案，并兼容当前商品枚举字段。

**Architecture:** 采用新增表和新增可空关联字段的兼容迁移，不删除现有 `StoreProduct.category`、`unit`、`supplier`。`StoreMasterDataService` 统一维护主数据约束，`StoreMasterDataInitializer` 初始化常用资料并回填旧商品；前端新增独立基础资料页面，现有 `/store` 继续作为经营台。

**Tech Stack:** Java 17、Spring Boot、Spring Data JPA、MySQL、JUnit 5、Mockito、Vue 3、TypeScript、Naive UI、Elegant Router。

---

## 文件结构

### 后端新增

- `src/main/java/com/yizhaoqi/smartpai/model/StoreMeasurementUnit.java`：计量单位实体。
- `src/main/java/com/yizhaoqi/smartpai/model/StoreProductCategory.java`：固定两级商品分类实体。
- `src/main/java/com/yizhaoqi/smartpai/model/StoreBusinessPartner.java`：统一客户/供应商实体。
- `src/main/java/com/yizhaoqi/smartpai/model/StoreWarehouse.java`：单默认仓库实体。
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreMeasurementUnitRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreProductCategoryRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreBusinessPartnerRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreWarehouseRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreMasterDataService.java`：主数据校验、保存、停用和操作人列表。
- `src/main/java/com/yizhaoqi/smartpai/service/StoreSkuGenerator.java`：自动 SKU 候选生成。
- `src/main/java/com/yizhaoqi/smartpai/config/StoreMasterDataInitializer.java`：默认资料初始化与旧字段回填。
- `src/main/java/com/yizhaoqi/smartpai/controller/StoreMasterDataController.java`：主数据 REST API。
- `src/test/java/com/yizhaoqi/smartpai/service/StoreMasterDataServiceTest.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreProductMasterDataTest.java`
- `src/test/java/com/yizhaoqi/smartpai/controller/StoreMasterDataControllerTest.java`

### 后端修改

- `src/main/java/com/yizhaoqi/smartpai/model/StoreProduct.java`
- `src/main/java/com/yizhaoqi/smartpai/repository/StoreProductRepository.java`
- `src/main/java/com/yizhaoqi/smartpai/service/StoreProductService.java`
- `src/main/java/com/yizhaoqi/smartpai/controller/StoreProductController.java`
- `src/main/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializer.java`
- `src/test/java/com/yizhaoqi/smartpai/service/StoreProductServiceTest.java`

### 前端新增

- `frontend/src/service/api/store-master-data.ts`
- `frontend/src/views/store-master-data/index.vue`
- `frontend/src/views/store-master-data/modules/unit-panel.vue`
- `frontend/src/views/store-master-data/modules/category-panel.vue`
- `frontend/src/views/store-master-data/modules/partner-panel.vue`
- `frontend/src/views/store-master-data/modules/warehouse-panel.vue`
- `frontend/src/views/store-master-data/modules/product-panel.vue`

### 前端修改或生成

- `frontend/src/service/api/index.ts`
- `frontend/src/service/api/store.ts`
- `frontend/src/router/elegant/imports.ts`
- `frontend/src/router/elegant/routes.ts`
- `frontend/src/router/elegant/transform.ts`
- `frontend/src/typings/elegant-router.d.ts`
- `frontend/src/locales/langs/zh-cn.ts`
- `frontend/src/locales/langs/en-us.ts`

## Task 1：锁定主数据约束的失败测试

**Files:**

- Create: `src/test/java/com/yizhaoqi/smartpai/service/StoreMasterDataServiceTest.java`

- [ ] **Step 1：写计量单位、两级分类和往来单位测试**

测试类使用 `@ExtendWith(MockitoExtension.class)`，声明四个主数据仓储和 `UserRepository` mock。先写以下核心用例：

```java
@Test
void shouldRejectThirdLevelCategory() {
    StoreProductCategory parent = new StoreProductCategory();
    parent.setId(20L);
    parent.setLevel(2);
    when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));

    CustomException ex = assertThrows(CustomException.class, () -> service.createCategory(
            new StoreMasterDataService.CategorySaveRequest("MONTHLY", "月抛", 20L, 10, true),
            "admin"
    ));

    assertEquals("STORE_CATEGORY_LEVEL_EXCEEDED", ex.getMessage());
}

@Test
void shouldRequireAtLeastOnePartnerRole() {
    CustomException ex = assertThrows(CustomException.class, () -> service.createPartner(
            new StoreMasterDataService.PartnerSaveRequest(
                    "P-001", "测试单位", null, null, null,
                    false, false, null, true, null
            ),
            "admin"
    ));

    assertEquals("STORE_PARTNER_ROLE_REQUIRED", ex.getMessage());
}

@Test
void shouldKeepOnlyOneEnabledDefaultWarehouse() {
    StoreWarehouse existing = new StoreWarehouse();
    existing.setId(1L);
    existing.setCode("DEFAULT");
    existing.setDefaultWarehouse(true);
    existing.setEnabled(true);
    when(warehouseRepository.findByDefaultWarehouseTrueAndEnabledTrue()).thenReturn(Optional.of(existing));

    CustomException ex = assertThrows(CustomException.class, () -> service.saveWarehouse(
            new StoreMasterDataService.WarehouseSaveRequest(
                    null, "SECOND", "第二仓库", "测试门店", null, null, true, true
            ),
            "admin"
    ));

    assertEquals("STORE_DEFAULT_WAREHOUSE_EXISTS", ex.getMessage());
}
```

测试类的 mock 与初始化固定为：

```java
@ExtendWith(MockitoExtension.class)
class StoreMasterDataServiceTest {
    @Mock StoreMeasurementUnitRepository unitRepository;
    @Mock StoreProductCategoryRepository categoryRepository;
    @Mock StoreBusinessPartnerRepository partnerRepository;
    @Mock StoreWarehouseRepository warehouseRepository;
    @Mock UserRepository userRepository;
    StoreMasterDataService service;

    @BeforeEach
    void setUp() {
        service = new StoreMasterDataService(
                unitRepository, categoryRepository, partnerRepository, warehouseRepository, userRepository
        );
    }
}
```

- [ ] **Step 2：运行测试并确认失败**

Run:

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreMasterDataServiceTest test
```

Expected: `StoreMasterDataService`、主数据实体或仓储尚不存在导致编译失败。

## Task 2：建立四类主数据实体和仓储

**Files:**

- Create: `src/main/java/com/yizhaoqi/smartpai/model/StoreMeasurementUnit.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/StoreProductCategory.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/StoreBusinessPartner.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/StoreWarehouse.java`
- Create: four repository files listed above

- [ ] **Step 1：实现实体的权威字段**

四个实体使用 `@Entity`、`@Comment`、`@CreationTimestamp`、`@UpdateTimestamp`，每个字段添加业务注释。字段契约必须与下列定义一致：

```java
// StoreMeasurementUnit
Long id;
String code;          // 唯一，最长 32
String name;          // 唯一，最长 32
Boolean enabled;
Integer sortOrder;
String createdBy;
String updatedBy;
LocalDateTime createdAt;
LocalDateTime updatedAt;

// StoreProductCategory
Long id;
String code;          // 全局唯一，最长 64
String name;
StoreProductCategory parent;
Integer level;        // 只允许 1 或 2
Boolean enabled;
Integer sortOrder;
String createdBy;
String updatedBy;
LocalDateTime createdAt;
LocalDateTime updatedAt;

// StoreBusinessPartner
Long id;
String code;
String name;
String contactName;
String phone;
String address;
Boolean customerEnabled;
Boolean supplierEnabled;
CustomerType customerType; // RETAIL 或 WHOLESALE，可空
Boolean enabled;
String remark;
String createdBy;
String updatedBy;
LocalDateTime createdAt;
LocalDateTime updatedAt;

// StoreWarehouse
Long id;
String code;
String name;
String storeName;
Long responsibleUserId;
String responsibleUsernameSnapshot;
Long purchaserUserId;
String purchaserUsernameSnapshot;
Boolean enabled;
Boolean defaultWarehouse;
String createdBy;
String updatedBy;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

- [ ] **Step 2：实现仓储方法**

```java
public interface StoreMeasurementUnitRepository extends JpaRepository<StoreMeasurementUnit, Long> {
    Optional<StoreMeasurementUnit> findByCodeIgnoreCase(String code);
    Optional<StoreMeasurementUnit> findByName(String name);
    List<StoreMeasurementUnit> findAllByOrderBySortOrderAscIdAsc();
}

public interface StoreProductCategoryRepository extends JpaRepository<StoreProductCategory, Long> {
    Optional<StoreProductCategory> findByCodeIgnoreCase(String code);
    boolean existsByParentIdAndName(Long parentId, String name);
    List<StoreProductCategory> findAllByOrderByLevelAscSortOrderAscIdAsc();
}

public interface StoreBusinessPartnerRepository extends JpaRepository<StoreBusinessPartner, Long> {
    Optional<StoreBusinessPartner> findByCodeIgnoreCase(String code);
    List<StoreBusinessPartner> findByCustomerEnabledTrueAndEnabledTrueOrderByNameAsc();
    List<StoreBusinessPartner> findBySupplierEnabledTrueAndEnabledTrueOrderByNameAsc();
}

public interface StoreWarehouseRepository extends JpaRepository<StoreWarehouse, Long> {
    Optional<StoreWarehouse> findByCodeIgnoreCase(String code);
    Optional<StoreWarehouse> findByDefaultWarehouseTrueAndEnabledTrue();
}
```

- [ ] **Step 3：编译确认实体映射有效**

Run:

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 编译通过；不得删除或重命名现有 Store 表字段。

## Task 3：实现主数据服务与权限接口

**Files:**

- Create: `src/main/java/com/yizhaoqi/smartpai/service/StoreMasterDataService.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/controller/StoreMasterDataController.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/config/PermissionCatalogInitializer.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/service/StoreMasterDataServiceTest.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/controller/StoreMasterDataControllerTest.java`

- [ ] **Step 1：实现服务校验与视图记录**

服务公开以下方法，所有保存方法使用 `@Transactional`，列表方法使用 `@Transactional(readOnly = true)`：

```java
List<UnitView> listUnits();
UnitView createUnit(UnitSaveRequest request, String operator);
UnitView updateUnit(Long id, UnitSaveRequest request, String operator);
List<CategoryView> listCategories();
CategoryView createCategory(CategorySaveRequest request, String operator);
CategoryView updateCategory(Long id, CategorySaveRequest request, String operator);
List<PartnerView> listPartners(PartnerRole role);
PartnerView createPartner(PartnerSaveRequest request, String operator);
PartnerView updatePartner(Long id, PartnerSaveRequest request, String operator);
WarehouseView getDefaultWarehouse();
WarehouseView saveWarehouse(WarehouseSaveRequest request, String operator);
List<OperatorView> listOperators();
```

`CategorySaveRequest.parentId == null` 时层级为 1；有父节点时父层级必须为 1，保存层级为 2。往来单位至少勾选一个角色；客户角色开启时 `customerType` 必填。仓库负责人和采购联系人通过 `UserRepository.findById` 校验并保存用户名快照。

请求、筛选枚举和主要视图记录固定为：

```java
public enum PartnerRole { ALL, CUSTOMER, SUPPLIER }
public record UnitSaveRequest(String code, String name, Boolean enabled, Integer sortOrder) {}
public record CategorySaveRequest(String code, String name, Long parentId,
                                  Integer sortOrder, Boolean enabled) {}
public record PartnerSaveRequest(String code, String name, String contactName, String phone,
                                 String address, Boolean customerEnabled, Boolean supplierEnabled,
                                 StoreBusinessPartner.CustomerType customerType,
                                 Boolean enabled, String remark) {}
public record WarehouseSaveRequest(Long id, String code, String name, String storeName,
                                   Long responsibleUserId, Long purchaserUserId,
                                   Boolean enabled, Boolean defaultWarehouse) {}
public record UnitView(Long id, String code, String name, boolean enabled, int sortOrder) {}
public record CategoryView(Long id, String code, String name, Long parentId,
                           int level, boolean enabled, int sortOrder) {}
public record PartnerView(Long id, String code, String name, String phone,
                          boolean customerEnabled, boolean supplierEnabled,
                          StoreBusinessPartner.CustomerType customerType, boolean enabled) {}
public record WarehouseView(Long id, String code, String name, String storeName,
                            Long responsibleUserId, String responsibleUsername,
                            Long purchaserUserId, String purchaserUsername,
                            boolean enabled, boolean defaultWarehouse) {}
public record OperatorView(Long id, String username, User.Role role) {}
```

- [ ] **Step 2：实现 REST 路径**

```text
GET/POST/PUT  /api/v1/store/master-data/units
GET/POST/PUT  /api/v1/store/master-data/categories
GET/POST/PUT  /api/v1/store/master-data/partners
GET/PUT       /api/v1/store/master-data/warehouse
GET           /api/v1/store/master-data/operators
```

Controller 查看方法使用 `store.master-data.view`，写方法使用 `store.master-data.manage`，响应继续采用 `{code, message, data}`。

`StoreMasterDataControllerTest` 使用反射锁定关键权限：

```java
@Test
void shouldProtectUnitWritesWithManagePermission() throws Exception {
    Method method = StoreMasterDataController.class.getMethod(
            "createUnit", StoreMasterDataService.UnitSaveRequest.class, Authentication.class
    );
    PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
    assertEquals("@permissionAuthorization.has(authentication, 'store.master-data.manage')", auth.value());
}
```

- [ ] **Step 3：登记权限**

在权限目录加入：

```java
seed("store.master-data.view", "store", "基础资料", "view", "查看门店基础资料"),
seed("store.master-data.manage", "store", "基础资料", "manage", "维护门店基础资料"),
```

- [ ] **Step 4：运行主数据测试**

Run:

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreMasterDataServiceTest,StoreMasterDataControllerTest test
```

Expected: 主数据约束、操作人校验和权限注解测试通过。

## Task 4：升级商品关联与自动 SKU

**Files:**

- Create: `src/main/java/com/yizhaoqi/smartpai/service/StoreSkuGenerator.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/StoreProduct.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/repository/StoreProductRepository.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/StoreProductService.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/controller/StoreProductController.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/service/StoreProductMasterDataTest.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/service/StoreProductServiceTest.java`

- [ ] **Step 1：写商品新契约失败测试**

```java
@Test
void shouldGenerateSkuAndPersistMasterDataRelations() {
    when(categoryRepository.findById(10L)).thenReturn(Optional.of(enabledCategory(10L)));
    when(unitRepository.findById(20L)).thenReturn(Optional.of(enabledUnit(20L)));
    when(skuGenerator.generateAvailableSku()).thenReturn("SP-20260711-A1B2C3D4");
    when(productRepository.save(any(StoreProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

    StoreProductService.ProductView view = service.createProduct(
            new StoreProductService.ProductCreateRequest(
                    null, "半年抛 -3.00", 10L, 20L, null,
                    null, null, null, null, null, null,
                    BigDecimal.TEN, BigDecimal.valueOf(30), 2,
                    true, true, false, 90
            ),
            "admin"
    );

    assertEquals("SP-20260711-A1B2C3D4", view.sku());
    assertEquals(10L, view.categoryId());
    assertEquals(20L, view.unitId());
}
```

`StoreProductMasterDataTest` 加入以下固定构造器：

```java
private StoreProductCategory enabledCategory(Long id) {
    StoreProductCategory category = new StoreProductCategory();
    category.setId(id);
    category.setCode("CONTACT_LENS");
    category.setName("隐形眼镜");
    category.setLevel(1);
    category.setEnabled(true);
    return category;
}

private StoreMeasurementUnit enabledUnit(Long id) {
    StoreMeasurementUnit unit = new StoreMeasurementUnit();
    unit.setId(id);
    unit.setCode("BOX");
    unit.setName("盒");
    unit.setEnabled(true);
    return unit;
}
```

- [ ] **Step 2：增加兼容关联字段**

在 `StoreProduct` 中新增，不删除旧 `category`、`unit`、`supplier`：

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private StoreProductCategory categoryRef;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "unit_id")
private StoreMeasurementUnit unitRef;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "default_supplier_id")
private StoreBusinessPartner defaultSupplierRef;

@Column(nullable = false)
private Boolean batchManaged;

@Column(nullable = false)
private Boolean expirationManaged;

@Column(nullable = false)
private Boolean productionDateRequired;

private Integer expiryWarningDays;
```

每个字段同时添加标准字段注释和 `@Comment`。

- [ ] **Step 3：实现 SKU 生成器**

```java
@Service
public class StoreSkuGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final StoreProductRepository productRepository;

    public StoreSkuGenerator(StoreProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public String generateAvailableSku() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 8).toUpperCase(Locale.ROOT);
            String candidate = "SP-" + LocalDate.now().format(DATE) + "-" + suffix;
            if (!productRepository.existsBySku(candidate)) return candidate;
        }
        throw new CustomException("STORE_PRODUCT_SKU_GENERATION_FAILED", HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 4：更新商品请求与校验**

`ProductCreateRequest` 使用 `categoryId`、`unitId`、`defaultSupplierId` 和四个批次配置字段。SKU 为空时调用生成器；手填 SKU 保持现有唯一性校验。分类、单位和默认供应商必须启用；默认供应商必须具有供应商角色。批次关闭时强制 `expirationManaged=false`、`productionDateRequired=false`。

- [ ] **Step 5：运行商品测试**

Run:

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreProductServiceTest,StoreProductMasterDataTest test
```

Expected: 自动 SKU、自定义 SKU、停用主数据拒绝和兼容视图测试通过。

## Task 5：初始化默认资料并兼容回填

**Files:**

- Create: `src/main/java/com/yizhaoqi/smartpai/config/StoreMasterDataInitializer.java`
- Create: `docs/databases/store-master-data-validation.sql`
- Test: `src/test/java/com/yizhaoqi/smartpai/config/StoreMasterDataInitializerTest.java`

- [ ] **Step 1：写幂等初始化测试**

测试连续调用两次 `run()`，断言单位、类别和默认仓库不会重复；已有旧商品只回填空关联，不覆盖已存在的新关联：

```java
@Test
void shouldSeedCatalogIdempotentlyAndPreserveExistingProductRelations() {
    StoreProduct product = new StoreProduct();
    product.setSku("EXISTING");
    product.setCategory(StoreProduct.ProductCategory.CONTACT_LENS);
    product.setUnit(StoreProduct.ProductUnit.BOX);
    StoreProductCategory existingCategory = new StoreProductCategory();
    existingCategory.setId(99L);
    product.setCategoryRef(existingCategory);
    when(productRepository.findAll()).thenReturn(List.of(product));

    initializer.run();
    initializer.run();

    verify(unitRepository, atMost(5)).save(any(StoreMeasurementUnit.class));
    verify(warehouseRepository, atMostOnce()).save(any(StoreWarehouse.class));
    assertSame(existingCategory, product.getCategoryRef());
}
```

仓储 mock 对默认编码返回同一实体，使第二次运行走“已存在”分支；不能用 `save` 次数掩盖重复创建。

- [ ] **Step 2：实现初始化目录**

初始化内容固定为：

```java
Map.of("BOX", "盒", "BOTTLE", "瓶", "PIECE", "片", "PAIR", "副", "ITEM", "个");
Map.of(
    "FRAME", "镜框", "LENS", "镜片", "SUNGLASSES", "太阳镜",
    "CONTACT_LENS", "隐形眼镜", "CARE_SOLUTION", "护理产品",
    "ACCESSORY", "配件", "OTHER", "其他"
);
```

创建 `DEFAULT` 默认仓库，名称为“默认仓库”、门店名为“默认门店”。旧 `StoreProduct.category`、`unit` 和 `supplier` 只在新关联为空时回填；供应商文本回填为具有供应商角色的往来单位。

- [ ] **Step 3：写只读核验 SQL**

SQL 必须覆盖：默认资料重复、商品孤立分类/单位、默认供应商角色错误、启用默认仓库数量不是 1。

- [ ] **Step 4：运行初始化测试与编译**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreMasterDataInitializerTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 测试与编译通过。

## Task 6：实现基础资料前端

**Files:** 前端新增和修改文件见“文件结构”。

- [ ] **Step 1：定义 API 类型与函数**

`store-master-data.ts` 至少导出：

```ts
export interface StoreUnitView { id: number; code: string; name: string; enabled: boolean; sortOrder: number }
export interface StoreCategoryView { id: number; code: string; name: string; parentId: number | null; level: 1 | 2; enabled: boolean; sortOrder: number }
export interface StorePartnerView { id: number; code: string; name: string; phone: string | null; customerEnabled: boolean; supplierEnabled: boolean; customerType: 'RETAIL' | 'WHOLESALE' | null; enabled: boolean }
export interface StoreWarehouseView { id: number; code: string; name: string; storeName: string; responsibleUserId: number | null; purchaserUserId: number | null; enabled: boolean; defaultWarehouse: boolean }
export interface StoreOperatorView { id: number; username: string; role: 'USER' | 'ADMIN' }
```

同时提供 units/categories/partners/warehouse/operators 的 GET 和保存函数。`store.ts` 的商品请求改用 ID 关联和批次配置，不再向新页面暴露固定 category/unit 枚举。

- [ ] **Step 2：实现五个职责单一的面板**

`index.vue` 只负责权限判断和 `NTabs`；每个面板自己加载数据、维护表单、处理错误和刷新。商品面板支持 SKU 留空自动生成，类别、单位和供应商使用可搜索选择器；仓库面板从 operators API 选择负责人和采购联系人。

- [ ] **Step 3：生成路由并补充翻译**

Run:

```powershell
Set-Location frontend
pnpm.CMD gen-route
```

Expected: 生成 `store-master-data` 路由和类型。随后在 `zh-cn.ts` 添加 `store-master-data: '基础资料'`，在 `en-us.ts` 添加 `store-master-data: 'Master Data'`。

- [ ] **Step 4：运行前端验证**

```powershell
Set-Location frontend
pnpm.CMD typecheck
pnpm.CMD exec eslint src/service/api/store-master-data.ts src/views/store-master-data --max-warnings=0
```

Expected: typecheck 和定向 ESLint 通过；若 ESLint 被既有插件环境阻断，记录原始错误并保持 typecheck 为硬门槛。

## Task 7：阶段 1 回归与提交

**Files:** 只包含本计划明确文件。

- [ ] **Step 1：运行后端定向回归**

```powershell
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=StoreMasterDataServiceTest,StoreMasterDataControllerTest,StoreMasterDataInitializerTest,StoreProductServiceTest,StoreProductMasterDataTest test
& 'D:\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

Expected: 全部通过。

- [ ] **Step 2：检查数据库结构和回填结果**

在真实 MySQL 执行 `docs/databases/store-master-data-validation.sql`。Expected: 默认单位/类别无重复、启用默认仓库恰好 1 个、商品无孤立的新主数据关联。

- [ ] **Step 3：检查暂存范围**

```powershell
git diff --check
git status --short
git diff --cached --stat
```

Expected: 不包含既有读路径治理、IDE、本地配置、工具目录和原始测试资料。

- [ ] **Step 4：提交阶段 1**

```powershell
git commit -m "feat(store): 建立眼镜店基础资料体系" -m "验证:`n- Store 主数据与商品定向测试`n- 后端编译`n- 前端 typecheck`n- 主数据只读核验 SQL"
```
