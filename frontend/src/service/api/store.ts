import { request } from '../request';

export type StoreProductCategory =
  | 'FRAME'
  | 'LENS'
  | 'SUNGLASSES'
  | 'CONTACT_LENS'
  | 'CARE_SOLUTION'
  | 'ACCESSORY'
  | 'OTHER';
export type StoreProductUnit = 'PAIR' | 'PIECE' | 'BOX' | 'BOTTLE' | 'ITEM';
export type StoreProductStatus = 'ENABLED' | 'DISABLED' | 'OUT_OF_STOCK' | 'DELISTED';
export type StoreStockStatus = 'NORMAL' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'DISABLED';
export type StorePaymentMethod = 'CASH' | 'WECHAT' | 'ALIPAY' | 'BANK_CARD' | 'OTHER';

export interface StoreDashboardView {
  productCount: number;
  riskStockCount: number;
  todayBillCount: number;
  todayActualAmount: number;
}

export interface StoreProductView {
  id: number;
  sku: string;
  name: string;
  category: StoreProductCategory;
  unit: StoreProductUnit;
  retailPrice: number;
  status: StoreProductStatus;
}

export interface StoreStockView {
  id: number;
  productSku: string;
  warehouseCode: string;
  currentQuantity: number;
  availableQuantity: number;
  safeStock: number;
  status: StoreStockStatus;
}

export interface StoreSalesBillView {
  id: number;
  billNo: string;
  customerName: string;
  customerPhone: string;
  purchaseDate: string;
  leftDegreeDisplay: string;
  rightDegreeDisplay: string;
  frameModel: string;
  lensModel: string;
  actualAmount: number;
  paymentMethod: StorePaymentMethod;
}

export interface StoreImportResult {
  successCount: number;
  failureCount: number;
}

export interface StoreProductCreateRequest {
  sku: string;
  name: string;
  category: StoreProductCategory;
  unit: StoreProductUnit;
  retailPrice: number;
  status: StoreProductStatus;
}

export interface StoreSalesBillCreateRequest {
  customerName: string;
  customerPhone: string;
  purchaseDate: string;
  leftMyopiaDegree: number | null;
  leftAstigmatism: number | null;
  leftAxis: number | null;
  rightMyopiaDegree: number | null;
  rightAstigmatism: number | null;
  rightAxis: number | null;
  pupillaryDistance: number | null;
  frameModel: string;
  lensModel: string;
  paymentAmount: number;
  discountAmount: number;
  actualAmount: number;
  paymentMethod: StorePaymentMethod;
  salesperson: string;
  optometrist: string;
  remark: string;
}

export function fetchStoreDashboard() {
  return request<StoreDashboardView>({ url: '/store/dashboard/summary' });
}

export function fetchStoreProducts() {
  return request<StoreProductView[]>({ url: '/store/products' });
}

export function fetchStoreStocks() {
  return request<StoreStockView[]>({ url: '/store/inventory/stocks' });
}

export function fetchStoreSalesBills() {
  return request<StoreSalesBillView[]>({ url: '/store/sales-bills' });
}

export function fetchCreateStoreProduct(data: StoreProductCreateRequest) {
  return request({ url: '/store/products', method: 'post', data });
}

export function fetchCreateStoreSalesBill(data: StoreSalesBillCreateRequest) {
  return request({ url: '/store/sales-bills', method: 'post', data });
}

export function fetchImportStoreSalesBills(data: FormData) {
  return request<StoreImportResult>({
    url: '/store/sales-bills/import',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}
