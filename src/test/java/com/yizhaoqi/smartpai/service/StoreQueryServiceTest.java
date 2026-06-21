package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.model.StoreSalesBill;
import com.yizhaoqi.smartpai.repository.StoreInventoryLedgerRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoreQueryServiceTest {

    private StoreProductRepository productRepository;
    private StoreInventoryStockRepository stockRepository;
    private StoreInventoryLedgerRepository ledgerRepository;
    private StoreSalesBillRepository salesBillRepository;
    private StoreQueryService service;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(StoreProductRepository.class);
        stockRepository = Mockito.mock(StoreInventoryStockRepository.class);
        ledgerRepository = Mockito.mock(StoreInventoryLedgerRepository.class);
        salesBillRepository = Mockito.mock(StoreSalesBillRepository.class);
        service = new StoreQueryService(productRepository, stockRepository, ledgerRepository, salesBillRepository);
    }

    @Test
    void shouldClampProductLimitAndReturnBusinessSource() {
        StoreProduct product = product("FRAME-A123", "A123 frame");
        when(productRepository.searchProducts(eq("FRAME-A123"), eq("A123"), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(List.of(product));

        StoreQueryService.QueryResult<StoreQueryService.ProductView> result = service.queryProducts(
                new StoreQueryService.ProductQuery("FRAME-A123", "A123", null, null, null, 200)
        );

        assertEquals("store_product", result.source());
        assertEquals("商品档案", result.sourceLabel());
        assertEquals(1, result.data().size());
        assertTrue(result.content().contains("FRAME-A123"));
        assertTrue(result.content().contains("来源：商品档案"));
    }

    @Test
    void shouldQueryWarningInventoryWithProductNames() {
        StoreInventoryStock stock = stock("FRAME-A123", StoreInventoryStock.StockStatus.LOW_STOCK, 2, 5);
        when(stockRepository.searchStocks(eq(null), eq(StoreInventoryStock.StockStatus.LOW_STOCK), eq(true), any(Pageable.class)))
                .thenReturn(List.of(stock));
        when(productRepository.findBySkuIn(List.of("FRAME-A123"))).thenReturn(List.of(product("FRAME-A123", "A123 frame")));

        StoreQueryService.QueryResult<StoreQueryService.InventoryView> result = service.queryInventory(
                new StoreQueryService.InventoryQuery(null, StoreInventoryStock.StockStatus.LOW_STOCK, true, 10)
        );

        assertEquals("store_inventory_stock", result.source());
        assertEquals("实时库存", result.sourceLabel());
        assertEquals("A123 frame", result.data().get(0).productName());
        assertTrue(result.content().contains("LOW_STOCK"));
        assertTrue(result.content().contains("来源：实时库存"));
    }

    @Test
    void shouldQueryStockFlowBySkuAndDateRange() {
        StoreInventoryLedger ledger = ledger("FRAME-A123", "IN-001");
        when(ledgerRepository.searchLedgers(
                eq("FRAME-A123"),
                eq(null),
                eq(StoreInventoryLedger.ChangeType.INBOUND),
                eq(LocalDateTime.of(2026, 6, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 30, 23, 59, 59)),
                any(Pageable.class)
        )).thenReturn(List.of(ledger));

        StoreQueryService.QueryResult<StoreQueryService.StockFlowView> result = service.queryStockFlows(
                new StoreQueryService.StockFlowQuery(
                        "FRAME-A123",
                        null,
                        StoreInventoryLedger.ChangeType.INBOUND,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        10
                )
        );

        assertEquals("store_inventory_ledger", result.source());
        assertEquals(1, result.data().size());
        assertTrue(result.content().contains("IN-001"));
        assertTrue(result.content().contains("来源：库存流水"));
    }

    @Test
    void shouldRejectSalesBillQueryWithoutPhoneWhenNameIsOnlyFilter() {
        CustomException exception = assertThrows(CustomException.class, () -> service.querySalesBills(
                new StoreQueryService.SalesBillQuery(null, "张三", null, null, null, 10)
        ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", exception.getMessage());
    }

    @Test
    void shouldRejectSalesBillQueryWithoutPhoneOrBillNo() {
        CustomException exception = assertThrows(CustomException.class, () -> service.querySalesBills(
                new StoreQueryService.SalesBillQuery(null, null, null, null, null, 10)
        ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", exception.getMessage());
        verifyNoInteractions(salesBillRepository);
    }

    @Test
    void shouldQuerySalesBillHistoryByExactPhone() {
        StoreSalesBill bill = new StoreSalesBill();
        bill.setBillNo("SB-001");
        bill.setCustomerName("张三");
        bill.setCustomerPhone("13800000000");
        bill.setPurchaseDate(LocalDate.of(2026, 6, 8));
        bill.setActualAmount(new BigDecimal("399.00"));
        bill.setLeftMyopiaDegree(new BigDecimal("-3.50"));
        bill.setLeftAstigmatism(new BigDecimal("-0.75"));
        bill.setLeftAxis(180);
        bill.setRightMyopiaDegree(new BigDecimal("-2.75"));
        bill.setRightAstigmatism(new BigDecimal("-0.50"));
        bill.setRightAxis(170);
        when(salesBillRepository.searchBills(
                eq("13800000000"),
                eq(null),
                eq(null),
                any(LocalDate.class),
                any(LocalDate.class),
                any(Pageable.class)
        )).thenReturn(List.of(bill));

        StoreQueryService.QueryResult<StoreQueryService.SalesBillView> result = service.querySalesBills(
                new StoreQueryService.SalesBillQuery("13800000000", null, null, null, null, 10)
        );

        assertEquals("store_sales_bill", result.source());
        assertEquals("销售账单", result.sourceLabel());
        assertEquals("SB-001", result.data().get(0).billNo());
        assertTrue(result.content().contains("-3.5--0.75-180"));
        assertTrue(result.content().contains("来源：销售账单"));
    }

    @Test
    void shouldReturnStoreStatsForDateRange() {
        when(salesBillRepository.countByPurchaseDateBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(3L);
        when(salesBillRepository.sumActualAmountByPurchaseDateBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(new BigDecimal("1299.00"));
        when(stockRepository.countByStatus(StoreInventoryStock.StockStatus.LOW_STOCK)).thenReturn(2L);
        when(stockRepository.countByStatus(StoreInventoryStock.StockStatus.OUT_OF_STOCK)).thenReturn(1L);

        StoreQueryService.QueryResult<StoreQueryService.StoreStatsView> result = service.queryStoreStats(
                new StoreQueryService.StoreStatsQuery(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "summary")
        );

        assertEquals("store_business_stats", result.source());
        assertEquals(3L, result.data().get(0).salesBillCount());
        assertEquals(new BigDecimal("1299.00"), result.data().get(0).actualAmount());
        assertTrue(result.content().contains("来源：门店经营统计"));
    }

    private StoreProduct product(String sku, String name) {
        StoreProduct product = new StoreProduct();
        product.setSku(sku);
        product.setName(name);
        product.setCategory(StoreProduct.ProductCategory.FRAME);
        product.setBrand("BrandA");
        product.setModel("A123");
        product.setRetailPrice(new BigDecimal("399.00"));
        product.setStatus(StoreProduct.ProductStatus.ENABLED);
        return product;
    }

    private StoreInventoryStock stock(String sku, StoreInventoryStock.StockStatus status, int quantity, int safeStock) {
        StoreInventoryStock stock = new StoreInventoryStock();
        stock.setProductSku(sku);
        stock.setWarehouseCode(StoreInventoryService.DEFAULT_WAREHOUSE_CODE);
        stock.setCurrentQuantity(quantity);
        stock.setAvailableQuantity(quantity);
        stock.setSafeStock(safeStock);
        stock.setStatus(status);
        return stock;
    }

    private StoreInventoryLedger ledger(String sku, String orderNo) {
        StoreInventoryLedger ledger = new StoreInventoryLedger();
        ledger.setProductSku(sku);
        ledger.setWarehouseCode(StoreInventoryService.DEFAULT_WAREHOUSE_CODE);
        ledger.setChangeType(StoreInventoryLedger.ChangeType.INBOUND);
        ledger.setBusinessOrderNo(orderNo);
        ledger.setQuantityBefore(0);
        ledger.setChangeQuantity(5);
        ledger.setQuantityAfter(5);
        ledger.setOperationSource(StoreInventoryLedger.OperationSource.INBOUND_ORDER);
        ledger.setOperatedAt(LocalDateTime.of(2026, 6, 8, 10, 0));
        return ledger;
    }
}
