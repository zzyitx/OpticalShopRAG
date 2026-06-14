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
export type StoreInboundType = 'PURCHASE' | 'RETURN' | 'PROFIT' | 'INITIAL' | 'OTHER';
export type StoreOutboundType = 'SALE' | 'LOSS' | 'RETURN' | 'DEFICIT' | 'INTERNAL' | 'OTHER';
export type StoreOrderStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';

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
  items: StoreSalesBillItemView[];
}

export interface StoreSalesBillItemView {
  id: number | null;
  productSku: string;
  productNameSnapshot: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
}

export interface StoreInventoryOrderView {
  id: number;
  orderNo: string;
  status: StoreOrderStatus;
}

export interface StoreLedgerView {
  id: number;
  productSku: string;
  businessOrderNo: string;
  quantityBefore: number;
  changeQuantity: number;
  quantityAfter: number;
  operationSource: string;
  operator: string;
  operatedAt: string;
}

export interface StoreChangeLogView {
  id: number;
  billId: number;
  billNo: string;
  beforeSnapshot: string;
  afterSnapshot: string;
  changedBy: string;
  changedAt: string;
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
  items: StoreSalesBillItemRequest[];
  autoOutbound: boolean;
}

export interface StoreSalesBillItemRequest {
  productSku: string;
  productNameSnapshot: string;
  quantity: number;
  unitPrice: number;
}

export interface StoreInboundCreateRequest {
  inboundType: StoreInboundType;
  supplier: string;
  remark: string;
  items: StoreSalesBillItemRequest[];
}

export interface StoreOutboundCreateRequest {
  outboundType: StoreOutboundType;
  relatedBillNo: string;
  remark: string;
  items: StoreSalesBillItemRequest[];
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

export function fetchStoreInbounds() {
  return request<StoreInventoryOrderView[]>({ url: '/store/inventory/inbounds' });
}

export function fetchStoreOutbounds() {
  return request<StoreInventoryOrderView[]>({ url: '/store/inventory/outbounds' });
}

export function fetchStoreLedgers(productSku?: string) {
  return request<StoreLedgerView[]>({ url: '/store/inventory/ledgers', params: { productSku } });
}

export function fetchCreateStoreProduct(data: StoreProductCreateRequest) {
  return request({ url: '/store/products', method: 'post', data });
}

export function fetchCreateStoreSalesBill(data: StoreSalesBillCreateRequest) {
  return request({ url: '/store/sales-bills', method: 'post', data });
}

export function fetchUpdateStoreSalesBill(id: number, data: Omit<StoreSalesBillCreateRequest, 'items' | 'autoOutbound'>) {
  return request({ url: `/store/sales-bills/${id}`, method: 'put', data });
}

export function fetchStoreCustomerHistory(customerPhone: string) {
  return request<StoreSalesBillView[]>({ url: '/store/sales-bills/history', params: { customerPhone } });
}

export function fetchStoreBillChanges(id: number) {
  return request<StoreChangeLogView[]>({ url: `/store/sales-bills/${id}/changes` });
}

export function fetchCreateStoreInbound(data: StoreInboundCreateRequest) {
  return request<StoreInventoryOrderView>({ url: '/store/inventory/inbounds', method: 'post', data });
}

export function fetchCreateStoreOutbound(data: StoreOutboundCreateRequest) {
  return request<StoreInventoryOrderView>({ url: '/store/inventory/outbounds', method: 'post', data });
}

export function fetchConfirmStoreInbound(id: number) {
  return request({ url: `/store/inventory/inbounds/${id}/confirm`, method: 'post' });
}

export function fetchCancelStoreInbound(id: number) {
  return request({ url: `/store/inventory/inbounds/${id}/cancel`, method: 'post' });
}

export function fetchConfirmStoreOutbound(id: number) {
  return request({ url: `/store/inventory/outbounds/${id}/confirm`, method: 'post' });
}

export function fetchCancelStoreOutbound(id: number) {
  return request({ url: `/store/inventory/outbounds/${id}/cancel`, method: 'post' });
}

export function fetchImportStoreSalesBills(data: FormData) {
  return request<StoreImportResult>({
    url: '/store/sales-bills/import',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

export function fetchStoreSalesBillTemplate() {
  return request<Blob, 'blob'>({
    url: '/store/sales-bills/template',
    responseType: 'blob'
  });
}
