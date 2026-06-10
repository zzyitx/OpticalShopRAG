<script setup lang="ts">
import { h } from 'vue';
import { NTag } from 'naive-ui';
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui';
import {
  fetchCreateStoreProduct,
  fetchCreateStoreSalesBill,
  fetchImportStoreSalesBills,
  fetchStoreDashboard,
  fetchStoreProducts,
  fetchStoreSalesBills,
  fetchStoreStocks
} from '@/service/api';
import type {
  StoreDashboardView,
  StorePaymentMethod,
  StoreProductCategory,
  StoreProductStatus,
  StoreProductUnit,
  StoreProductView,
  StoreSalesBillView,
  StoreStockView
} from '@/service/api';

const loading = ref(false);
const dashboard = ref<StoreDashboardView>({
  productCount: 0,
  riskStockCount: 0,
  todayBillCount: 0,
  todayActualAmount: 0
});
const products = ref<StoreProductView[]>([]);
const stocks = ref<StoreStockView[]>([]);
const bills = ref<StoreSalesBillView[]>([]);
const productForm = reactive({
  sku: '',
  name: '',
  category: 'FRAME' as StoreProductCategory,
  unit: 'PIECE' as StoreProductUnit,
  retailPrice: 0,
  status: 'ENABLED' as StoreProductStatus
});
const billForm = reactive({
  customerName: '',
  customerPhone: '',
  purchaseDate: new Date().toISOString().slice(0, 10),
  leftMyopiaDegree: null as number | null,
  leftAstigmatism: null as number | null,
  leftAxis: null as number | null,
  rightMyopiaDegree: null as number | null,
  rightAstigmatism: null as number | null,
  rightAxis: null as number | null,
  pupillaryDistance: null as number | null,
  frameModel: '',
  lensModel: '',
  paymentAmount: 0,
  discountAmount: 0,
  actualAmount: 0,
  paymentMethod: 'CASH' as StorePaymentMethod,
  salesperson: '',
  optometrist: '',
  remark: ''
});

const metricCards = computed(() => [
  { label: '商品档案', value: dashboard.value.productCount },
  { label: '库存预警', value: dashboard.value.riskStockCount },
  { label: '今日账单', value: dashboard.value.todayBillCount },
  { label: '今日实收', value: `￥${Number(dashboard.value.todayActualAmount || 0).toFixed(2)}` }
]);

const categoryOptions: Array<{ label: string; value: StoreProductCategory }> = [
  { label: '镜架', value: 'FRAME' },
  { label: '镜片', value: 'LENS' },
  { label: '太阳镜', value: 'SUNGLASSES' },
  { label: '隐形眼镜', value: 'CONTACT_LENS' },
  { label: '护理液', value: 'CARE_SOLUTION' },
  { label: '配件', value: 'ACCESSORY' },
  { label: '其他', value: 'OTHER' }
];
const unitOptions: Array<{ label: string; value: StoreProductUnit }> = [
  { label: '件', value: 'PIECE' },
  { label: '副', value: 'PAIR' },
  { label: '盒', value: 'BOX' },
  { label: '瓶', value: 'BOTTLE' },
  { label: '个', value: 'ITEM' }
];
const paymentOptions = [
  { label: '现金', value: 'CASH' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '银行卡', value: 'BANK_CARD' },
  { label: '其他', value: 'OTHER' }
];

const productColumns: DataTableColumns<StoreProductView> = [
  { title: 'SKU', key: 'sku', minWidth: 120 },
  { title: '名称', key: 'name', minWidth: 160 },
  { title: '零售价', key: 'retailPrice', width: 90 }
];
const stockColumns: DataTableColumns<StoreStockView> = [
  { title: 'SKU', key: 'productSku', minWidth: 120 },
  { title: '数量', key: 'currentQuantity', width: 80 },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: row => {
      const item = renderStockStatus(row.status);
      return h(NTag, { type: item.type }, { default: () => item.label });
    }
  }
];
const billColumns: DataTableColumns<StoreSalesBillView> = [
  { title: '单号', key: 'billNo', minWidth: 150 },
  { title: '客户', key: 'customerName', width: 90 },
  { title: '实收', key: 'actualAmount', width: 80 }
];

async function loadStoreData() {
  loading.value = true;
  try {
    // 看板卡片和三类业务列表彼此独立，并行加载可以缩短经营台首次渲染时间。
    const [dashboardRes, productsRes, stocksRes, billsRes] = await Promise.all([
      fetchStoreDashboard(),
      fetchStoreProducts(),
      fetchStoreStocks(),
      fetchStoreSalesBills()
    ]);
    if (!dashboardRes.error && dashboardRes.data) dashboard.value = dashboardRes.data;
    if (!productsRes.error && productsRes.data) products.value = productsRes.data;
    if (!stocksRes.error && stocksRes.data) stocks.value = stocksRes.data;
    if (!billsRes.error && billsRes.data) bills.value = billsRes.data;
  } finally {
    loading.value = false;
  }
}

async function createProduct() {
  const { error } = await fetchCreateStoreProduct(productForm);
  if (!error) {
    window.$message?.success('商品已保存');
    Object.assign(productForm, {
      sku: '',
      name: '',
      category: 'FRAME',
      unit: 'PIECE',
      retailPrice: 0,
      status: 'ENABLED'
    });
    await loadStoreData();
  }
}

async function createBill() {
  const { error } = await fetchCreateStoreSalesBill(billForm);
  if (!error) {
    window.$message?.success('销售单已保存');
    Object.assign(billForm, {
      customerName: '',
      customerPhone: '',
      purchaseDate: new Date().toISOString().slice(0, 10),
      frameModel: '',
      lensModel: '',
      paymentAmount: 0,
      discountAmount: 0,
      actualAmount: 0,
      salesperson: '',
      optometrist: '',
      remark: ''
    });
    await loadStoreData();
  }
}

function downloadTemplate() {
  window.open('/proxy-default/store/sales-bills/template', '_blank');
}

async function importBills(options: UploadCustomRequestOptions) {
  const rawFile = options.file.file;
  if (!rawFile) return;
  const formData = new FormData();
  formData.append('file', rawFile);
  // 后端逐行导入有效账单，并返回成功/失败汇总供操作人员核对。
  const { error, data } = await fetchImportStoreSalesBills(formData);
  if (!error) {
    window.$message?.success(`导入完成：成功 ${data?.successCount || 0} 条，失败 ${data?.failureCount || 0} 条`);
    options.onFinish();
    await loadStoreData();
  }
}

function renderStockStatus(status: StoreStockView['status']) {
  const statusMap = {
    NORMAL: { label: '正常', type: 'success' },
    LOW_STOCK: { label: '低库存', type: 'warning' },
    OUT_OF_STOCK: { label: '无库存', type: 'error' },
    DISABLED: { label: '停用', type: 'default' }
  } as const;
  return statusMap[status];
}

onMounted(loadStoreData);
</script>

<template>
  <NSpace vertical :size="16" class="store-page">
    <div class="store-toolbar">
      <div>
        <h2>眼镜店经营台</h2>
        <p>商品、库存和配镜账单的阶段一工作面</p>
      </div>
      <NSpace>
        <NButton secondary @click="downloadTemplate">模板</NButton>
        <NUpload :show-file-list="false" accept=".csv" :custom-request="importBills">
          <NButton secondary>导入账单</NButton>
        </NUpload>
        <NButton type="primary" :loading="loading" @click="loadStoreData">刷新</NButton>
      </NSpace>
    </div>

    <NGrid :cols="4" :x-gap="12" :y-gap="12" responsive="screen">
      <NGi v-for="item in metricCards" :key="item.label">
        <NCard size="small" :bordered="false" class="metric-card">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </NCard>
      </NGi>
    </NGrid>

    <NGrid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
      <NGi>
        <NCard title="新增商品" size="small">
          <NForm label-placement="top">
            <NGrid :cols="2" :x-gap="12" :y-gap="10" responsive="screen">
              <NGi><NFormItem label="SKU"><NInput v-model:value="productForm.sku" /></NFormItem></NGi>
              <NGi><NFormItem label="名称"><NInput v-model:value="productForm.name" /></NFormItem></NGi>
              <NGi><NFormItem label="类别"><NSelect v-model:value="productForm.category" :options="categoryOptions" /></NFormItem></NGi>
              <NGi><NFormItem label="单位"><NSelect v-model:value="productForm.unit" :options="unitOptions" /></NFormItem></NGi>
              <NGi><NFormItem label="零售价"><NInputNumber v-model:value="productForm.retailPrice" class="w-full" /></NFormItem></NGi>
            </NGrid>
            <NButton type="primary" block @click="createProduct">保存商品</NButton>
          </NForm>
        </NCard>
      </NGi>

      <NGi>
        <NCard title="新增销售单" size="small">
          <NForm label-placement="top">
            <NGrid :cols="3" :x-gap="12" :y-gap="10" responsive="screen">
              <NGi><NFormItem label="客户"><NInput v-model:value="billForm.customerName" /></NFormItem></NGi>
              <NGi><NFormItem label="手机"><NInput v-model:value="billForm.customerPhone" /></NFormItem></NGi>
              <NGi><NFormItem label="日期"><NInput v-model:value="billForm.purchaseDate" /></NFormItem></NGi>
              <NGi><NFormItem label="左近视"><NInputNumber v-model:value="billForm.leftMyopiaDegree" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="左散光"><NInputNumber v-model:value="billForm.leftAstigmatism" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="左轴位"><NInputNumber v-model:value="billForm.leftAxis" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="右近视"><NInputNumber v-model:value="billForm.rightMyopiaDegree" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="右散光"><NInputNumber v-model:value="billForm.rightAstigmatism" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="右轴位"><NInputNumber v-model:value="billForm.rightAxis" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="镜架"><NInput v-model:value="billForm.frameModel" /></NFormItem></NGi>
              <NGi><NFormItem label="镜片"><NInput v-model:value="billForm.lensModel" /></NFormItem></NGi>
              <NGi><NFormItem label="实收"><NInputNumber v-model:value="billForm.actualAmount" class="w-full" /></NFormItem></NGi>
              <NGi><NFormItem label="方式"><NSelect v-model:value="billForm.paymentMethod" :options="paymentOptions" /></NFormItem></NGi>
            </NGrid>
            <NButton type="primary" block @click="createBill">保存销售单</NButton>
          </NForm>
        </NCard>
      </NGi>
    </NGrid>

    <NGrid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
      <NGi>
        <NCard title="商品档案" size="small">
          <NDataTable
            size="small"
            :data="products"
            :columns="productColumns"
            :pagination="{ pageSize: 8 }"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard title="库存" size="small">
          <NDataTable
            size="small"
            :data="stocks"
            :columns="stockColumns"
            :pagination="{ pageSize: 8 }"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard title="销售单" size="small">
          <NDataTable
            size="small"
            :data="bills"
            :columns="billColumns"
            :pagination="{ pageSize: 8 }"
          />
        </NCard>
      </NGi>
    </NGrid>
  </NSpace>
</template>

<style scoped>
.store-page {
  padding: 16px;
}

.store-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.store-toolbar h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.store-toolbar p {
  margin: 4px 0 0;
  color: var(--n-text-color-3);
}

.metric-card {
  min-height: 88px;
}

.metric-label {
  color: var(--n-text-color-3);
  font-size: 13px;
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
}
</style>
