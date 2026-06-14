<script setup lang="ts">
import { h } from 'vue';
import { NButton, NSpace, NTag } from 'naive-ui';
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui';
import {
  fetchCancelStoreInbound,
  fetchCancelStoreOutbound,
  fetchConfirmStoreInbound,
  fetchConfirmStoreOutbound,
  fetchCreateStoreInbound,
  fetchCreateStoreOutbound,
  fetchCreateStoreProduct,
  fetchCreateStoreSalesBill,
  fetchImportStoreSalesBills,
  fetchStoreBillChanges,
  fetchStoreCustomerHistory,
  fetchStoreDashboard,
  fetchStoreInbounds,
  fetchStoreLedgers,
  fetchStoreOutbounds,
  fetchStoreProducts,
  fetchStoreSalesBillTemplate,
  fetchStoreSalesBills,
  fetchStoreStocks,
  fetchUpdateStoreSalesBill
} from '@/service/api';
import type {
  StoreChangeLogView,
  StoreDashboardView,
  StoreInventoryOrderView,
  StoreLedgerView,
  StorePaymentMethod,
  StoreProductCategory,
  StoreProductStatus,
  StoreProductUnit,
  StoreProductView,
  StoreSalesBillCreateRequest,
  StoreSalesBillView,
  StoreStockView
} from '@/service/api';

const loading = ref(false);
const authStore = useAuthStore();
const canViewDashboard = computed(() => authStore.hasPermission('store.dashboard.view'));
const canViewProducts = computed(() => authStore.hasPermission('store.product.view'));
const canCreateProduct = computed(() => authStore.hasPermission('store.product.create'));
const canViewInventory = computed(() => authStore.hasPermission('store.inventory.view'));
const canCreateInbound = computed(() => authStore.hasPermission('store.inventory.inbound.create'));
const canCreateOutbound = computed(() => authStore.hasPermission('store.inventory.outbound.create'));
const canViewSalesBills = computed(() => authStore.hasPermission('store.sales-bill.view'));
const canCreateSalesBill = computed(() => authStore.hasPermission('store.sales-bill.create'));
const canUpdateSalesBill = computed(() => authStore.hasPermission('store.sales-bill.update'));
const canImportSalesBills = computed(() => authStore.hasPermission('store.sales-bill.import'));
const canDownloadTemplate = computed(() => authStore.hasPermission('store.sales-bill.template.download'));
const hasStoreAccess = computed(() => canViewDashboard.value || canViewProducts.value || canViewInventory.value || canViewSalesBills.value);
const dashboard = ref<StoreDashboardView>({ productCount: 0, riskStockCount: 0, todayBillCount: 0, todayActualAmount: 0 });
const products = ref<StoreProductView[]>([]);
const stocks = ref<StoreStockView[]>([]);
const bills = ref<StoreSalesBillView[]>([]);
const inbounds = ref<StoreInventoryOrderView[]>([]);
const outbounds = ref<StoreInventoryOrderView[]>([]);
const ledgers = ref<StoreLedgerView[]>([]);
const customerHistory = ref<StoreSalesBillView[]>([]);
const changeLogs = ref<StoreChangeLogView[]>([]);
const historyPhone = ref('');
const editingBillId = ref<number | null>(null);

const productForm = reactive({
  sku: '',
  name: '',
  category: 'FRAME' as StoreProductCategory,
  unit: 'PIECE' as StoreProductUnit,
  retailPrice: 0,
  status: 'ENABLED' as StoreProductStatus
});
const inboundForm = reactive({ productSku: '', quantity: 1, unitPrice: 0, supplier: '' });
const outboundForm = reactive({ productSku: '', quantity: 1, unitPrice: 0, relatedBillNo: '' });
const billForm = reactive<StoreSalesBillCreateRequest>({
  customerName: '',
  customerPhone: '',
  purchaseDate: new Date().toISOString().slice(0, 10),
  leftMyopiaDegree: null,
  leftAstigmatism: null,
  leftAxis: null,
  rightMyopiaDegree: null,
  rightAstigmatism: null,
  rightAxis: null,
  pupillaryDistance: null,
  frameModel: '',
  lensModel: '',
  paymentAmount: 0,
  discountAmount: 0,
  actualAmount: 0,
  paymentMethod: 'CASH' as StorePaymentMethod,
  salesperson: '',
  optometrist: '',
  remark: '',
  items: [],
  autoOutbound: true
});
const billItemForm = reactive({ productSku: '', productNameSnapshot: '', quantity: 1, unitPrice: 0 });

const metricCards = computed(() => [
  { label: '商品档案', value: dashboard.value.productCount },
  { label: '库存预警', value: dashboard.value.riskStockCount },
  { label: '今日账单', value: dashboard.value.todayBillCount },
  { label: '今日实收', value: `￥${Number(dashboard.value.todayActualAmount || 0).toFixed(2)}` }
]);
const productOptions = computed(() => products.value.map(item => ({ label: `${item.sku} - ${item.name}`, value: item.sku })));
const categoryOptions = ['FRAME', 'LENS', 'SUNGLASSES', 'CONTACT_LENS', 'CARE_SOLUTION', 'ACCESSORY', 'OTHER'].map(value => ({ label: value, value }));
const unitOptions = ['PIECE', 'PAIR', 'BOX', 'BOTTLE', 'ITEM'].map(value => ({ label: value, value }));
const paymentOptions = ['CASH', 'WECHAT', 'ALIPAY', 'BANK_CARD', 'OTHER'].map(value => ({ label: value, value }));

function orderColumns(kind: 'inbound' | 'outbound'): DataTableColumns<StoreInventoryOrderView> {
  return [
    { title: '单号', key: 'orderNo', minWidth: 180 },
    { title: '状态', key: 'status', width: 100 },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: row =>
        row.status === 'DRAFT' && (kind === 'inbound' ? canCreateInbound.value : canCreateOutbound.value)
          ? h(NSpace, {}, () => [
              h(NButton, { size: 'small', type: 'primary', onClick: () => mutateOrder(kind, row.id, 'confirm') }, () => '确认'),
              h(NButton, { size: 'small', onClick: () => mutateOrder(kind, row.id, 'cancel') }, () => '取消')
            ])
          : '-'
    }
  ];
}

const productColumns: DataTableColumns<StoreProductView> = [
  { title: 'SKU', key: 'sku', minWidth: 120 },
  { title: '名称', key: 'name', minWidth: 160 },
  { title: '零售价', key: 'retailPrice', width: 90 }
];
const stockColumns: DataTableColumns<StoreStockView> = [
  { title: 'SKU', key: 'productSku', minWidth: 120 },
  { title: '数量', key: 'currentQuantity', width: 80 },
  { title: '状态', key: 'status', width: 100, render: row => h(NTag, { type: row.status === 'NORMAL' ? 'success' : 'warning' }, () => row.status) }
];
const billColumns: DataTableColumns<StoreSalesBillView> = [
  { title: '单号', key: 'billNo', minWidth: 180 },
  { title: '客户', key: 'customerName', width: 90 },
  { title: '实收', key: 'actualAmount', width: 80 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: row =>
      h(NSpace, {}, () => [
        canUpdateSalesBill.value ? h(NButton, { size: 'small', onClick: () => editBill(row) }, () => '编辑') : null,
        h(NButton, { size: 'small', onClick: () => loadChanges(row.id) }, () => '修改记录')
      ])
  }
];
const ledgerColumns: DataTableColumns<StoreLedgerView> = [
  { title: 'SKU', key: 'productSku', width: 130 },
  { title: '业务单号', key: 'businessOrderNo', minWidth: 180 },
  { title: '变动前', key: 'quantityBefore', width: 80 },
  { title: '变化量', key: 'changeQuantity', width: 80 },
  { title: '变动后', key: 'quantityAfter', width: 80 }
];

async function loadStoreData() {
  loading.value = true;
  try {
    const tasks: Promise<void>[] = [];
    if (canViewDashboard.value) tasks.push(fetchStoreDashboard().then(({ error, data }) => { if (!error && data) dashboard.value = data; }));
    if (canViewProducts.value) tasks.push(fetchStoreProducts().then(({ error, data }) => { if (!error && data) products.value = data; }));
    if (canViewInventory.value) {
      tasks.push(fetchStoreStocks().then(({ error, data }) => { if (!error && data) stocks.value = data; }));
      tasks.push(fetchStoreInbounds().then(({ error, data }) => { if (!error && data) inbounds.value = data; }));
      tasks.push(fetchStoreOutbounds().then(({ error, data }) => { if (!error && data) outbounds.value = data; }));
      tasks.push(fetchStoreLedgers().then(({ error, data }) => { if (!error && data) ledgers.value = data; }));
    }
    if (canViewSalesBills.value) tasks.push(fetchStoreSalesBills().then(({ error, data }) => { if (!error && data) bills.value = data; }));
    await Promise.all(tasks);
  } finally {
    loading.value = false;
  }
}

async function createProduct() {
  const { error } = await fetchCreateStoreProduct(productForm);
  if (!error) {
    window.$message?.success('商品已保存');
    Object.assign(productForm, { sku: '', name: '', category: 'FRAME', unit: 'PIECE', retailPrice: 0, status: 'ENABLED' });
    await loadStoreData();
  }
}

async function createInbound() {
  const { error } = await fetchCreateStoreInbound({
    inboundType: 'PURCHASE', supplier: inboundForm.supplier, remark: '', items: [{ ...inboundForm, productNameSnapshot: '' }]
  });
  if (!error) {
    window.$message?.success('入库草稿已创建');
    await loadStoreData();
  }
}

async function createOutbound() {
  const { error } = await fetchCreateStoreOutbound({
    outboundType: 'OTHER', relatedBillNo: outboundForm.relatedBillNo, remark: '', items: [{ ...outboundForm, productNameSnapshot: '' }]
  });
  if (!error) {
    window.$message?.success('出库草稿已创建');
    await loadStoreData();
  }
}

async function mutateOrder(kind: 'inbound' | 'outbound', id: number, action: 'confirm' | 'cancel') {
  const fn = kind === 'inbound'
    ? action === 'confirm' ? fetchConfirmStoreInbound : fetchCancelStoreInbound
    : action === 'confirm' ? fetchConfirmStoreOutbound : fetchCancelStoreOutbound;
  const { error } = await fn(id);
  if (!error) await loadStoreData();
}

function addBillItem() {
  if (editingBillId.value) return;
  if (!billItemForm.productSku || billItemForm.quantity <= 0) return;
  const product = products.value.find(item => item.sku === billItemForm.productSku);
  billForm.items.push({ ...billItemForm, productNameSnapshot: product?.name || billItemForm.productSku });
  Object.assign(billItemForm, { productSku: '', productNameSnapshot: '', quantity: 1, unitPrice: 0 });
}

async function saveBill() {
  const response = editingBillId.value
    ? await fetchUpdateStoreSalesBill(editingBillId.value, billForm)
    : await fetchCreateStoreSalesBill(billForm);
  if (!response.error) {
    window.$message?.success(editingBillId.value ? '账单已修改' : '账单已保存');
    resetBill();
    await loadStoreData();
  }
}

function editBill(row: StoreSalesBillView) {
  editingBillId.value = row.id;
  Object.assign(billForm, row, { items: [], autoOutbound: false });
}

function resetBill() {
  editingBillId.value = null;
  Object.assign(billForm, {
    customerName: '', customerPhone: '', purchaseDate: new Date().toISOString().slice(0, 10),
    frameModel: '', lensModel: '', paymentAmount: 0, discountAmount: 0, actualAmount: 0,
    salesperson: '', optometrist: '', remark: '', items: [], autoOutbound: true
  });
}

async function queryHistory() {
  const { error, data } = await fetchStoreCustomerHistory(historyPhone.value);
  if (!error && data) customerHistory.value = data;
}

async function loadChanges(id: number) {
  const { error, data } = await fetchStoreBillChanges(id);
  if (!error && data) changeLogs.value = data;
}

async function downloadTemplate() {
  const { error, data } = await fetchStoreSalesBillTemplate();
  if (error || !data) return;

  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'store-sales-bill-template.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}

async function importBills(options: UploadCustomRequestOptions) {
  const rawFile = options.file.file;
  if (!rawFile) return;
  const formData = new FormData();
  formData.append('file', rawFile);
  const { error, data } = await fetchImportStoreSalesBills(formData);
  if (!error) {
    window.$message?.success(`导入完成：成功 ${data?.successCount || 0} 条，失败 ${data?.failureCount || 0} 条`);
    options.onFinish();
    await loadStoreData();
  }
}

onMounted(loadStoreData);
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard :bordered="false" size="small" class="card-wrapper">
      <div class="store-toolbar">
        <div>
          <h2>眼镜店经营台</h2>
          <p>商品、库存、销售账单与客户配镜历史</p>
        </div>
        <NSpace>
          <NButton v-if="canDownloadTemplate" secondary @click="downloadTemplate">Excel 模板</NButton>
          <NUpload v-if="canImportSalesBills" :show-file-list="false" accept=".xlsx,.csv" :custom-request="importBills">
            <NButton secondary>导入账单</NButton>
          </NUpload>
          <NButton type="primary" :loading="loading" @click="loadStoreData">刷新</NButton>
        </NSpace>
      </div>
    </NCard>

    <NGrid v-if="canViewDashboard" responsive="screen" cols="1 s:2 m:4" :x-gap="16" :y-gap="16">
      <NGi v-for="item in metricCards" :key="item.label">
        <NCard :bordered="false" size="small" class="card-wrapper">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </NCard>
      </NGi>
    </NGrid>

    <NCard v-if="hasStoreAccess" :bordered="false" size="small" class="card-wrapper">
      <NTabs type="line" animated>
        <NTabPane v-if="canViewProducts" name="products" tab="商品档案">
          <NGrid responsive="screen" cols="1 s:2 m:3 l:6" :x-gap="8" :y-gap="8">
            <NGi><NInput v-model:value="productForm.sku" placeholder="SKU" /></NGi>
            <NGi><NInput v-model:value="productForm.name" placeholder="名称" /></NGi>
            <NGi><NSelect v-model:value="productForm.category" :options="categoryOptions" /></NGi>
            <NGi><NSelect v-model:value="productForm.unit" :options="unitOptions" /></NGi>
            <NGi><NInputNumber v-model:value="productForm.retailPrice" class="w-full" /></NGi>
            <NGi><NButton v-if="canCreateProduct" type="primary" @click="createProduct">保存商品</NButton></NGi>
          </NGrid>
          <NDataTable class="mt-12px" :columns="productColumns" :data="products" />
        </NTabPane>

        <NTabPane v-if="canViewInventory" name="inventory" tab="库存管理">
          <NGrid responsive="screen" cols="1 l:2" :x-gap="16" :y-gap="16">
            <NGi><NCard title="库存" size="small"><NDataTable :columns="stockColumns" :data="stocks" /></NCard></NGi>
            <NGi><NCard title="库存流水" size="small"><NDataTable :columns="ledgerColumns" :data="ledgers" /></NCard></NGi>
          </NGrid>
        </NTabPane>

        <NTabPane v-if="canViewInventory" name="inbound" tab="入库管理">
          <NSpace>
            <NSelect v-model:value="inboundForm.productSku" class="w-220px" :options="productOptions" />
            <NInputNumber v-model:value="inboundForm.quantity" />
            <NInputNumber v-model:value="inboundForm.unitPrice" />
            <NButton v-if="canCreateInbound" type="primary" @click="createInbound">创建入库草稿</NButton>
          </NSpace>
          <NDataTable class="mt-12px" :columns="orderColumns('inbound')" :data="inbounds" />
        </NTabPane>

        <NTabPane v-if="canViewInventory" name="outbound" tab="出库管理">
          <NSpace>
            <NSelect v-model:value="outboundForm.productSku" class="w-220px" :options="productOptions" />
            <NInputNumber v-model:value="outboundForm.quantity" />
            <NInputNumber v-model:value="outboundForm.unitPrice" />
            <NButton v-if="canCreateOutbound" type="primary" @click="createOutbound">创建出库草稿</NButton>
          </NSpace>
          <NDataTable class="mt-12px" :columns="orderColumns('outbound')" :data="outbounds" />
        </NTabPane>

        <NTabPane v-if="canViewSalesBills" name="sales-bills" tab="销售账单">
          <NCard :title="editingBillId ? '编辑账单' : '新增销售账单'" size="small">
            <NGrid responsive="screen" cols="1 s:2 m:3 l:6" :x-gap="8" :y-gap="8">
              <NGi><NInput v-model:value="billForm.customerName" placeholder="客户姓名" /></NGi>
              <NGi><NInput v-model:value="billForm.customerPhone" placeholder="手机号" /></NGi>
              <NGi><NInput v-model:value="billForm.purchaseDate" placeholder="日期" /></NGi>
              <NGi><NInputNumber v-model:value="billForm.actualAmount" class="w-full" placeholder="实收" /></NGi>
              <NGi><NSelect v-model:value="billForm.paymentMethod" :options="paymentOptions" /></NGi>
              <NGi><NSwitch v-model:value="billForm.autoOutbound" :disabled="Boolean(editingBillId)"><template #checked>自动出库</template><template #unchecked>不出库</template></NSwitch></NGi>
            </NGrid>
            <NSpace class="mt-12px">
              <NSelect v-model:value="billItemForm.productSku" class="w-220px" :options="productOptions" :disabled="Boolean(editingBillId)" />
              <NInputNumber v-model:value="billItemForm.quantity" :disabled="Boolean(editingBillId)" />
              <NInputNumber v-model:value="billItemForm.unitPrice" :disabled="Boolean(editingBillId)" />
              <NButton :disabled="Boolean(editingBillId)" @click="addBillItem">添加商品明细</NButton>
              <NButton v-if="editingBillId ? canUpdateSalesBill : canCreateSalesBill" type="primary" @click="saveBill">{{ editingBillId ? '保存修改' : '保存账单' }}</NButton>
              <NButton v-if="editingBillId" @click="resetBill">取消编辑</NButton>
            </NSpace>
            <div class="mt-8px text-12px text-gray">{{ editingBillId ? '编辑账单不调整商品明细与库存；库存变化请使用入库或出库单。' : `已添加 ${billForm.items.length} 条商品明细` }}</div>
          </NCard>
          <NDataTable class="mt-12px" :columns="billColumns" :data="bills" />
        </NTabPane>

        <NTabPane v-if="canViewSalesBills" name="customer-history" tab="客户历史">
          <NSpace>
            <NInput v-model:value="historyPhone" placeholder="输入手机号" />
            <NButton type="primary" @click="queryHistory">查询</NButton>
          </NSpace>
          <NGrid responsive="screen" cols="1 l:2" :x-gap="16" :y-gap="16" class="mt-12px">
            <NGi><NCard title="客户配镜历史" size="small"><NDataTable :columns="billColumns" :data="customerHistory" /></NCard></NGi>
            <NGi><NCard title="账单修改记录" size="small"><NDataTable :columns="[{ title: '账单号', key: 'billNo' }, { title: '修改前', key: 'beforeSnapshot' }, { title: '修改后', key: 'afterSnapshot' }]" :data="changeLogs" /></NCard></NGi>
          </NGrid>
        </NTabPane>
      </NTabs>
    </NCard>
    <NCard v-else :bordered="false" size="small" class="card-wrapper">
      <NEmpty description="当前账号尚未获得眼镜店经营台权限，请联系管理员在权限中心配置。" />
    </NCard>
  </div>
</template>

<style scoped>
.store-toolbar { display: flex; align-items: center; justify-content: space-between; }
.store-toolbar h2 { margin: 0; }
.store-toolbar p { margin: 4px 0 0; color: #64748b; }
.metric-label { color: #64748b; }
.metric-value { margin-top: 8px; font-size: 24px; font-weight: 700; }
</style>
