# 眼镜店真实样例测试验收包实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development`（推荐）或 `executing-plans` 按任务执行；所有步骤使用复选框跟踪。

**目标：** 从两份眼镜店真实样例生成脱敏、可复算的完整验收包，并在当前 PaiSmart 本地运行态完成业务、数据、浏览器、AI 与 RAG 验证，最后清理全部测试数据。

**架构：** 使用独立 Python 生成器完成源文件解析、脱敏、度数转换、用例目录和验收文件输出；生成器本身使用 `unittest` 做确定性校验。运行态验收只通过现有页面/API 写入业务数据，MySQL 用于核对及最终定向清理；浏览器验证复用真实登录态并保存脱敏证据。

**技术栈：** Python 3、`openpyxl`、`pypdf`、`reportlab`、`unittest`、Spring Boot REST API、Vue 3、MySQL、Chrome CDP、Markdown、SQL。

---

## 文件结构

### 新增生成器

- `scripts/glasses_store_validation/__init__.py`：声明生成器包。
- `scripts/glasses_store_validation/models.py`：定义批次、客户历史和测试用例数据模型。
- `scripts/glasses_store_validation/source_parser.py`：解析 PDF/Excel、脱敏并转换验光度数。
- `scripts/glasses_store_validation/case_catalog.py`：定义 116 条用例和两条业务旅程。
- `scripts/glasses_store_validation/artifact_writer.py`：生成两个 Excel、SQL、AI 问题集和执行报告模板。
- `scripts/glasses_store_validation/build_package.py`：命令行入口，定位源文件并一次性构建验收包。
- `scripts/glasses_store_validation/tests/test_source_parser.py`：验证 41 批次、28 光度、56 片、17 条客户历史和度数转换。
- `scripts/glasses_store_validation/tests/test_case_catalog.py`：验证 116 条用例的编号、模块数量和必要字段。
- `scripts/glasses_store_validation/tests/test_artifact_writer.py`：验证生成文件、工作表、表头、公式和脱敏结果。

### 新增验收产物

- `测试用例/眼镜店项目验收包/01-脱敏测试数据.xlsx`
- `测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx`
- `测试用例/眼镜店项目验收包/03-数据核验SQL.sql`
- `测试用例/眼镜店项目验收包/04-AI标准问题与期望结果.md`
- `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- `测试用例/眼镜店项目验收包/06-脱敏补货单.pdf`
- `测试用例/眼镜店项目验收包/evidence/.gitkeep`

原始 PDF、原始 Excel 和现有业务代码不修改。

## Task 1：建立源数据模型与 PDF 解析

**Files:**

- Create: `scripts/glasses_store_validation/__init__.py`
- Create: `scripts/glasses_store_validation/models.py`
- Create: `scripts/glasses_store_validation/source_parser.py`
- Create: `scripts/glasses_store_validation/tests/__init__.py`
- Create: `scripts/glasses_store_validation/tests/test_source_parser.py`

- [ ] **Step 1：先写 PDF 解析失败测试**

在 `test_source_parser.py` 中使用真实 PDF，并断言业务不变量：

```python
from collections import Counter
from pathlib import Path
import unittest

from scripts.glasses_store_validation.source_parser import parse_inbound_pdf


class SourceParserTest(unittest.TestCase):
    def test_pdf_contains_41_batches_28_powers_and_56_pieces(self):
        pdf = next(path for path in Path("测试用例").glob("*.pdf") if path.stat().st_size == 52217)
        rows = parse_inbound_pdf(pdf)
        self.assertEqual(41, len(rows))
        self.assertEqual(56, sum(row.quantity for row in rows))
        by_power = Counter(row.power for row in rows)
        self.assertEqual(28, len(by_power))
        self.assertEqual({2}, {sum(row.quantity for row in rows if row.power == power) for power in by_power})
```

- [ ] **Step 2：运行测试并确认因实现不存在而失败**

Run:

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.glasses_store_validation.tests.test_source_parser -v
```

Expected: `ImportError` 或 `ModuleNotFoundError`，指向尚未创建的 `source_parser`。

- [ ] **Step 3：实现最小 PDF 模型和解析器**

在 `models.py` 定义：

```python
from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class InboundBatch:
    source_page: int
    product_name: str
    power: str
    batch_no: str
    expiration_date: date
    registration_no: str
    manufacturer: str
    license_no: str
    quantity: int
    conclusion: str
```

在 `source_parser.py` 中实现 `parse_inbound_pdf(path: Path) -> list[InboundBatch]`。使用 `pypdf.PdfReader` 提取两页文本，并使用已经验证可匹配 41 行的模式：

```python
PDF_ROW_PATTERN = re.compile(
    r"(-\d+\.\d{2})\s+\u2571\s+JB\s+(\d+)\s+(\d{8})\s+"
    r"[\s\S]{0,220}?(\d+)\u53f7\s+(\d+)\s+\S+"
)
```

解析时固定脱敏商品名为“视倍舒半年抛隐形眼镜 1片装”，注册证号和生产企业从文档公共产品信息填入，日期按 `%Y%m%d` 转为 `date`。若匹配数量不是 41、数量合计不是 56 或光度数不是 28，立即抛出 `ValueError`，避免生成静默缺行的数据集。

- [ ] **Step 4：运行 PDF 测试并确认通过**

Run: 与 Step 2 相同。

Expected: PDF 用例 `PASS`。

- [ ] **Step 5：提交源模型与 PDF 解析**

```powershell
git add scripts/glasses_store_validation/__init__.py scripts/glasses_store_validation/models.py scripts/glasses_store_validation/source_parser.py scripts/glasses_store_validation/tests/__init__.py scripts/glasses_store_validation/tests/test_source_parser.py
git commit -m "testdata(store): parse optical inbound sample"
```

## Task 2：解析客户历史、脱敏并转换度数

**Files:**

- Modify: `scripts/glasses_store_validation/models.py`
- Modify: `scripts/glasses_store_validation/source_parser.py`
- Modify: `scripts/glasses_store_validation/tests/test_source_parser.py`

- [ ] **Step 1：添加客户历史和度数转换测试**

```python
from decimal import Decimal

from scripts.glasses_store_validation.source_parser import parse_customer_xlsx, parse_eye_degree


def test_compound_degree_is_split(self):
    degree = parse_eye_degree("-250-75*135")
    self.assertEqual((Decimal("-2.50"), Decimal("-0.75"), 135), degree)

def test_astigmatism_only_degree_uses_zero_sphere(self):
    degree = parse_eye_degree("-75*170")
    self.assertEqual((Decimal("0.00"), Decimal("-0.75"), 170), degree)

def test_customer_workbook_has_17_rows_and_no_real_identity(self):
    xlsx = next(Path("测试用例").glob("*.xlsx"))
    rows = parse_customer_xlsx(xlsx)
    self.assertEqual(17, len(rows))
    self.assertEqual({"测试客户甲", "测试客户乙"}, {row.customer_name for row in rows})
    self.assertTrue(all(row.customer_phone in {"16600000001", "16600000002"} for row in rows))
    self.assertTrue(any(row.manual_review_reason for row in rows))
```

- [ ] **Step 2：运行测试并确认新增断言失败**

Run:

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.glasses_store_validation.tests.test_source_parser -v
```

Expected: 客户历史和度数函数尚未实现导致失败，PDF 测试继续通过。

- [ ] **Step 3：实现客户模型、度数解析和脱敏**

在 `models.py` 增加 `CustomerHistory`，字段包含来源行号、脱敏姓名、测试手机号、购买日期、左右眼原始值、左右眼近视/散光/轴位、原始 ADD、瞳距、镜框、镜片、备注和人工复核原因。

在 `source_parser.py` 实现：

```python
def parse_eye_degree(value: object) -> tuple[Decimal | None, Decimal | None, int | None]:
    if value in (None, ""):
        return None, None, None
    text = str(value).strip()
    if "*" in text and text.count("-") == 1 and text.startswith("-"):
        cylinder, axis = text.split("*", 1)
        return Decimal("0.00"), Decimal(cylinder) / 100, int(axis)
    match = re.fullmatch(r"([+-]?\d+)([+-]\d+)\*(\d+)", text)
    if match:
        return Decimal(match.group(1)) / 100, Decimal(match.group(2)) / 100, int(match.group(3))
    return Decimal(text) / 100, None, None
```

`parse_customer_xlsx()` 只读取第一个工作表的 17 条非空数据行；按首次出现顺序映射为“测试客户甲/乙”和固定脱敏手机号 `16600000001/16600000002`。`ADD+75`、数字 `1` 和无法确认的孤立数字只进入原始字段和人工复核原因，不生成确定性数值。

- [ ] **Step 4：运行源数据测试并确认全部通过**

Run: 与 Step 2 相同。

Expected: 解析测试全部 `PASS`，控制台不出现真实姓名和手机号。

- [ ] **Step 5：提交客户历史转换**

```powershell
git add scripts/glasses_store_validation/models.py scripts/glasses_store_validation/source_parser.py scripts/glasses_store_validation/tests/test_source_parser.py
git commit -m "testdata(store): anonymize prescription history"
```

## Task 3：建立 116 条用例目录

**Files:**

- Create: `scripts/glasses_store_validation/case_catalog.py`
- Create: `scripts/glasses_store_validation/tests/test_case_catalog.py`

- [ ] **Step 1：先写目录完整性测试**

```python
import unittest
from collections import Counter

from scripts.glasses_store_validation.case_catalog import build_cases, build_journeys


EXPECTED_COUNTS = {
    "样例解析与数据质量": 10,
    "商品档案": 10,
    "入库、库存、流水": 16,
    "出库与销售库存一致性": 16,
    "账单导入与客户历史": 14,
    "仪表盘与经营统计": 8,
    "AI结构化查询": 16,
    "RAG与混合查询": 10,
    "权限与客户隐私": 8,
    "异常与恢复能力": 8,
}


class CaseCatalogTest(unittest.TestCase):
    def test_catalog_has_exact_module_counts_and_unique_ids(self):
        cases = build_cases()
        self.assertEqual(116, len(cases))
        self.assertEqual(116, len({case.case_id for case in cases}))
        self.assertEqual(EXPECTED_COUNTS, Counter(case.module for case in cases))
        self.assertTrue(all(case.precondition and case.steps and case.expected for case in cases))

    def test_two_business_journeys_exist(self):
        journeys = build_journeys()
        self.assertEqual(["JOURNEY-INBOUND-001", "JOURNEY-CUSTOMER-001"], [item.journey_id for item in journeys])
```

- [ ] **Step 2：运行目录测试并确认失败**

Run:

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.glasses_store_validation.tests.test_case_catalog -v
```

Expected: `case_catalog` 尚不存在导致失败。

- [ ] **Step 3：实现用例模型和显式目录**

在 `models.py` 增加 `TestCase` 与 `BusinessJourney` 数据类。每条 `TestCase` 必须包含：`case_id`、`priority`、`module`、`title`、`type`、`precondition`、`steps`、`test_data`、`expected`、`execution_layer`。

在 `case_catalog.py` 中显式定义以下编号区间，不使用复制标题凑数：

| 编号区间 | 数量 | 必须包含的主题 |
| --- | ---: | --- |
| `DATA-001..010` | 10 | PDF 行数/合计/光度、批次重复、Excel 行数、复合度数、仅散光、ADD 风险、重复行、脱敏 |
| `PROD-001..010` | 10 | 正常新增、必填、重复 SKU、分类、单位、价格、停用、下架、列表、详情 |
| `INV-001..016` | 16 | 入库草稿、41 明细、确认、重复确认、取消、状态、56 总库存、每光度 2、流水守恒、筛选、批次字段缺口 |
| `SALE-001..016` | 16 | 出库草稿、确认、库存不足、回滚、自动出库、无明细账单、商品状态、金额、重复请求、关联账单 |
| `BILL-001..014` | 14 | 模板、标准导入、原表头、缺手机号、列数、日期、度数、客户历史、同名隔离、修改快照、重复导入 |
| `STAT-001..008` | 8 | 今日账单、实收额、商品数、库存预警、默认 30 天、日期边界、空数据、数据库复算 |
| `AI-001..016` | 16 | 五工具正向、限制 50、日期上限 366 天、手机号约束、无数据、参数错误、来源标签、事实一致性 |
| `RAG-001..010` | 10 | 56 合计、指定光度批次、失效日期、注册证、引用预览、混合来源、历史数量不冒充库存、无依据拒答 |
| `AUTH-001..008` | 8 | 未登录、店长、店员、成本权限观察、姓名泛查拒绝、手机号隔离、证据脱敏、越权请求 |
| `ERR-001..008` | 8 | 非法枚举、非法轴位、负金额、零数量、重复确认、依赖异常、AI 工具失败、恢复后重试 |

- [ ] **Step 4：运行目录测试并确认 116 条全部满足约束**

Run: 与 Step 2 相同。

Expected: 两项目录测试均 `PASS`。

- [ ] **Step 5：提交用例目录**

```powershell
git add scripts/glasses_store_validation/models.py scripts/glasses_store_validation/case_catalog.py scripts/glasses_store_validation/tests/test_case_catalog.py
git commit -m "test(store): define optical shop acceptance catalog"
```

## Task 4：生成 Excel、SQL、AI 问题集和报告模板

**Files:**

- Create: `scripts/glasses_store_validation/artifact_writer.py`
- Create: `scripts/glasses_store_validation/build_package.py`
- Create: `scripts/glasses_store_validation/tests/test_artifact_writer.py`
- Create: `测试用例/眼镜店项目验收包/evidence/.gitkeep`

- [ ] **Step 1：先写产物结构测试**

```python
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from openpyxl import load_workbook

from scripts.glasses_store_validation.build_package import build_package


class ArtifactWriterTest(unittest.TestCase):
    def test_package_contains_required_files_and_sheets(self):
        with TemporaryDirectory() as directory:
            output = Path(directory)
            build_package(Path("测试用例"), output)
            self.assertEqual(
                {
                    "01-脱敏测试数据.xlsx",
                    "02-项目验收测试用例.xlsx",
                    "03-数据核验SQL.sql",
                    "04-AI标准问题与期望结果.md",
                    "05-执行与缺陷报告.md",
                    "06-脱敏补货单.pdf",
                },
                {path.name for path in output.iterdir() if path.is_file()},
            )
            data_book = load_workbook(output / "01-脱敏测试数据.xlsx", data_only=False)
            self.assertEqual(
                ["使用说明", "补货批次", "商品档案", "客户历史-原始脱敏", "账单导入-标准", "异常数据"],
                data_book.sheetnames,
            )
            case_book = load_workbook(output / "02-项目验收测试用例.xlsx", data_only=False)
            self.assertEqual(117, case_book["执行用例"].max_row)
```

- [ ] **Step 2：运行测试并确认失败**

Run:

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.glasses_store_validation.tests.test_artifact_writer -v
```

Expected: 构建入口不存在导致失败。

- [ ] **Step 3：实现工作簿写入器**

`artifact_writer.py` 必须：

- 创建中文表头、冻结首行、启用筛选、设置合理列宽和自动换行。
- 在测试数据工作簿中保留来源页码/行号、原始脱敏值、标准字段、人工复核原因和接口支持状态。
- 商品档案使用 `TST-OPT-<执行日期>-CL-0100` 形式的唯一 SKU；28 个光度一一对应。
- 标准账单导入表严格使用现有 19 列模板：`customerName` 至 `remark`。
- 用例工作簿包含“执行说明”“覆盖矩阵”“执行用例”“业务旅程”“执行汇总”“缺陷记录”六张表。
- “执行汇总”使用 `COUNTIF` 公式统计通过、失败、阻断、暂不支持、数据风险和未执行。
- 所有用例初始状态为“未执行”，实际结果、证据路径和缺陷编号留空。
- 使用 `reportlab` 和 `STSong-Light` 生成 `06-脱敏补货单.pdf`，只写商品名称、光度、批号、失效日期、注册证号、生产企业、许可证号、数量和结论；不写真实收货单位、地址、联系人、电话或发货方联系方式。

- [ ] **Step 4：实现 SQL、AI 问题集和报告模板**

`03-数据核验SQL.sql` 只包含 `SELECT`，至少包括：

```sql
SELECT COUNT(*) AS product_count
FROM store_product
WHERE sku LIKE 'TST-OPT-%';

SELECT SUM(current_quantity) AS total_quantity,
       MIN(current_quantity) AS min_quantity,
       MAX(current_quantity) AS max_quantity
FROM store_inventory_stock
WHERE product_sku LIKE 'TST-OPT-%-CL-%';

SELECT COUNT(*) AS broken_ledger_count
FROM store_inventory_ledger
WHERE product_sku LIKE 'TST-OPT-%'
  AND quantity_before + change_quantity <> quantity_after;
```

`04-AI标准问题与期望结果.md` 按结构化、RAG、混合、拒答四组列出问题、预期工具、权威来源和关键断言。`05-执行与缺陷报告.md` 预置环境、汇总、两条旅程、缺陷、能力缺口、数据风险、清理结果和最终结论栏目，不预填通过结论。

- [ ] **Step 5：实现命令行构建入口**

`build_package.py` 提供：

```python
def build_package(source_dir: Path, output_dir: Path) -> None:
    batches = parse_inbound_pdf(resolve_pdf(source_dir))
    customers = parse_customer_xlsx(resolve_customer_xlsx(source_dir))
    cases = build_cases()
    journeys = build_journeys()
    write_all_artifacts(output_dir, batches, customers, cases, journeys)
```

脚本默认源目录为仓库根目录的 `测试用例`，输出目录为 `测试用例/眼镜店项目验收包`。每次构建只覆盖生成器负责的六个产物，不删除 `evidence/` 中的执行证据。

- [ ] **Step 6：运行全部生成器测试并确认通过**

Run:

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest discover -s scripts/glasses_store_validation/tests -v
```

Expected: 源解析、目录和产物测试全部 `PASS`。

- [ ] **Step 7：提交生成器**

```powershell
git add scripts/glasses_store_validation 测试用例/眼镜店项目验收包/evidence/.gitkeep
git commit -m "test(store): generate optical shop validation package"
```

## Task 5：构建并静态验收正式产物

**Files:**

- Create: `测试用例/眼镜店项目验收包/01-脱敏测试数据.xlsx`
- Create: `测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx`
- Create: `测试用例/眼镜店项目验收包/03-数据核验SQL.sql`
- Create: `测试用例/眼镜店项目验收包/04-AI标准问题与期望结果.md`
- Create: `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- Create: `测试用例/眼镜店项目验收包/06-脱敏补货单.pdf`

- [ ] **Step 1：运行正式构建**

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m scripts.glasses_store_validation.build_package
```

Expected: 六个产物创建成功，原始 PDF/Excel 的哈希和修改时间不变。

- [ ] **Step 2：重新打开两个工作簿做结构校验**

使用 `openpyxl.load_workbook` 重新加载，断言：补货批次 41 行、商品光度 SKU 28 个、客户历史 17 行、执行用例 116 条、用例 ID 无重复、所有公式可读取。

- [ ] **Step 3：检查脱敏和 SQL 安全性**

Run:

```powershell
Select-String -LiteralPath '测试用例\眼镜店项目验收包\03-数据核验SQL.sql' -Pattern '^\s*(DELETE|UPDATE|INSERT|DROP|TRUNCATE)\b'
```

再使用 `openpyxl` 读取两个生成工作簿，确认客户姓名仅为测试别名、手机号仅为 `16600000001/16600000002`；使用 `pypdf` 提取 `06-脱敏补货单.pdf` 文本，确认不含“收货单位、收货地址、收货人、收货电话、发货地址、发货电话”等身份字段。

Expected: SQL 命令无匹配，工作簿身份字段和 PDF 白名单检查均通过。

- [ ] **Step 4：视觉检查 Excel**

将两个工作簿的每张表渲染或打开检查，确认中文不乱码、标题可见、冻结窗格和筛选生效、列宽可读、状态公式引用正确。

- [ ] **Step 5：提交正式产物**

```powershell
git add 测试用例/眼镜店项目验收包/01-脱敏测试数据.xlsx 测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx 测试用例/眼镜店项目验收包/03-数据核验SQL.sql 测试用例/眼镜店项目验收包/04-AI标准问题与期望结果.md 测试用例/眼镜店项目验收包/05-执行与缺陷报告.md 测试用例/眼镜店项目验收包/06-脱敏补货单.pdf
git commit -m "testdata(store): add optical shop acceptance package"
```

## Task 6：记录代码和运行环境基线

**Files:**

- Modify: `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- Create: `测试用例/眼镜店项目验收包/evidence/baseline-services.txt`
- Create: `测试用例/眼镜店项目验收包/evidence/baseline-store-counts.tsv`

- [ ] **Step 1：记录现有工作区和端口，不覆盖用户改动**

记录 `git status --short`，检测 `9527`、`8081`、`3306`、Redis、Elasticsearch、Kafka 和 MinIO 实际配置地址的可达性。敏感配置只记录“可达/不可达”，不记录密码和密钥。

- [ ] **Step 2：执行后端编译和定向测试**

```powershell
mvn -q -DskipTests compile
mvn -q '-Dtest=Store*Test,AgentToolRegistryTest' test
```

Expected: 编译通过，定向测试通过；若失败，将完整命令、失败测试和首个根因写入报告，不修改业务代码。

- [ ] **Step 3：执行前端静态验证**

```powershell
Set-Location frontend
pnpm typecheck
pnpm exec eslint src/views/store/index.vue src/views/chat/modules/chat-message.vue src/service/api/store.ts
```

Expected: 类型检查和定向 lint 通过；现有基线失败单独记录。

- [ ] **Step 4：记录 Store 表基线**

通过 `mysql --batch --skip-column-names` 记录十张 `store_*` 表的行数和最大主键，输出到 `baseline-store-counts.tsv`。MySQL 密码通过当前进程环境传入，不写入命令、报告或证据。

- [ ] **Step 5：恢复真实运行态**

若 `8081`/`9527` 仍未监听，按仓库约定启动后端和前端；后台进程使用隐藏窗口，日志写入 `.codex-tmp/glasses-store-validation/`。等待 `/api/v1` 健康响应和 `http://localhost:9527/#/store` 页面可访问后继续。

## Task 7：执行结构化业务与数据一致性用例

**Files:**

- Modify: `测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx`
- Modify: `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- Create: `测试用例/眼镜店项目验收包/evidence/business-api.jsonl`
- Create: `测试用例/眼镜店项目验收包/evidence/business-sql.tsv`
- Create: `测试用例/眼镜店项目验收包/evidence/created-records.json`

- [ ] **Step 1：通过真实登录态进入经营台**

使用 Chrome CDP 复用登录态，打开 `http://localhost:9527/#/store`，确认页面、控制台和首屏网络请求。若没有有效登录态，通过现有登录页面登录，凭据只从本地安全配置读取，不进入证据。

- [ ] **Step 2：执行商品和补货旅程**

通过页面/API 创建 28 个光度 SKU 和控制商品，创建包含 41 条明细的入库草稿并确认。捕获响应中的商品 ID、入库单 ID、单号、库存 ID 和流水 ID 到 `created-records.json`。

- [ ] **Step 3：核对补货不变量**

执行核验 SQL，必须得到：测试隐形眼镜 SKU 数为 28、库存总量为 56、每个 SKU 数量为 2、测试入库流水为 41、流水守恒失败数为 0。页面库存列表和接口响应必须与 SQL 一致。

- [ ] **Step 4：执行账单导入和客户旅程**

先验证原始表头导入失败，再导入标准化历史账单。按脱敏手机号分别查询两名同名测试客户；创建近期带商品明细的自动出库账单，验证账单、销售出库单、库存和流水在同一业务动作后保持一致。

- [ ] **Step 5：执行反向、边界和权限用例**

依次验证重复 SKU、零/负数量、停用商品、库存不足、重复确认、缺失手机号、错误列数、非法日期、无效枚举、未登录访问和越权请求。事务失败后复查库存和流水没有部分写入。

- [ ] **Step 6：回填用例工作簿**

为 `DATA-*`、`PROD-*`、`INV-*`、`SALE-*`、`BILL-*`、`STAT-*`、`AUTH-*` 中已执行的用例填写实际结果、状态、证据路径和缺陷编号。未具备第二角色或外部依赖的用例标记“阻断”，不得填“通过”。

## Task 8：执行 AI 结构化、RAG 和混合问答

**Files:**

- Modify: `测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx`
- Modify: `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- Create: `测试用例/眼镜店项目验收包/evidence/ai-tool-events.jsonl`
- Create: `测试用例/眼镜店项目验收包/evidence/ai-network.jsonl`
- Create: `测试用例/眼镜店项目验收包/evidence/screenshots/`

- [ ] **Step 1：上传脱敏 PDF 并等待索引完成**

上传生成的 `06-脱敏补货单.pdf`，观察上传、解析、向量化和索引状态。记录文件 ID、组织标签和最终状态；不把 Token 或真实门店信息写入证据。

- [ ] **Step 2：执行 16 条 AI 结构化查询**

覆盖 `query_product`、`query_inventory`、`query_stock_flow`、`query_sales_bill`、`query_store_stats`，以及 limit 上限、366 天日期上限、手机号约束、无数据、非法参数和来源标签。每题记录工具名、参数、工具结果、最终回答和对应 SQL 值。

- [ ] **Step 3：执行 10 条 RAG 与混合查询**

至少核验总数量 56、指定光度的两个批次、最近/最早失效日期、注册证号、生产企业、引用预览、实时库存与 PDF 历史数量分离、混合来源展示和无依据拒答。

- [ ] **Step 4：检查浏览器证据**

每条关键问题检查控制台、网络响应、工具事件、最终回答和引用预览。结构化来源不得进入文档引用映射；文档引用必须能打开正确片段。

- [ ] **Step 5：回填 AI/RAG 用例**

填写 `AI-*`、`RAG-*` 和剩余 `ERR-*` 的实际结果。模型措辞允许变化，但工具选择、事实值、来源类型、引用和拒答行为按硬断言判定。

## Task 9：汇总缺陷、清理数据并完成报告

**Files:**

- Modify: `测试用例/眼镜店项目验收包/02-项目验收测试用例.xlsx`
- Modify: `测试用例/眼镜店项目验收包/05-执行与缺陷报告.md`
- Create: `测试用例/眼镜店项目验收包/evidence/final-store-counts.tsv`
- Create: `测试用例/眼镜店项目验收包/evidence/cleanup-log.txt`

- [ ] **Step 1：汇总状态和缺陷**

确认 116 条用例均为“通过、失败、阻断、暂不支持、数据风险”之一，不保留“未执行”。缺陷记录包含严重级别、复现步骤、预期、实际、证据、影响和修复建议；不修改业务代码。

- [ ] **Step 2：通过业务能力清理可清理对象**

取消仍为草稿的入库/出库单，删除 RAG 测试文件并等待 MySQL 文件记录、Elasticsearch 片段和 MinIO 对象清理完成。

- [ ] **Step 3：事务内定向清理已确认 Store 数据**

根据 `created-records.json` 的主键和测试前缀，先执行只读预览并确认目标集合全部属于本轮测试，再按变更日志、账单明细、账单、出库明细、出库单、入库明细、入库单、流水、库存、商品的外键顺序删除。任何目标包含非测试记录时立即回滚并标记阻断。

- [ ] **Step 4：复核执行前后基线**

再次记录十张 Store 表行数、测试前缀记录、测试手机号记录和测试文件记录。Expected: 测试记录均为 0，非测试基线行数与执行前一致。

- [ ] **Step 5：完成项目效果结论**

报告分别给出：业务闭环通过率、AI/RAG 通过率、失败与阻断、能力缺口、数据风险、两条业务旅程结论和是否达到可演示/可验收状态。批号、失效日期和 ADD 字段问题必须出现在能力缺口章节。

- [ ] **Step 6：运行最终静态验证**

```powershell
& 'C:\Users\28279\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest discover -s scripts/glasses_store_validation/tests -v
git diff --check
```

Expected: 生成器测试全部通过；本次新增文本文件没有空白错误。业务代码既有行尾提示单独记录，不改写用户文件。

- [ ] **Step 7：定向提交验收结果**

只暂存生成器、验收包和脱敏证据；逐项检查 staged diff，确认没有原始样例、凭据、Token、Cookie、真实手机号和用户既有改动后提交：

```powershell
git commit -m "test(store): complete optical shop acceptance validation"
```
