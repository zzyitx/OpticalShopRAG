package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class StoreDashboardServiceTest {

    private StoreProductRepository productRepository;
    private StoreInventoryStockRepository stockRepository;
    private StoreSalesBillRepository salesBillRepository;
    private StoreDashboardService service;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(StoreProductRepository.class);
        stockRepository = Mockito.mock(StoreInventoryStockRepository.class);
        salesBillRepository = Mockito.mock(StoreSalesBillRepository.class);
        service = new StoreDashboardService(productRepository, stockRepository, salesBillRepository);
    }

    @Test
    void shouldReturnPhaseOneStoreSummary() {
        LocalDate today = LocalDate.now();
        when(productRepository.count()).thenReturn(12L);
        when(stockRepository.countByStatus(StoreInventoryStock.StockStatus.LOW_STOCK)).thenReturn(2L);
        when(stockRepository.countByStatus(StoreInventoryStock.StockStatus.OUT_OF_STOCK)).thenReturn(1L);
        when(salesBillRepository.countByPurchaseDate(today)).thenReturn(3L);
        when(salesBillRepository.sumActualAmountByPurchaseDate(today)).thenReturn(new BigDecimal("1299.00"));

        StoreDashboardService.StoreDashboardView view = service.getSummary();

        assertEquals(12L, view.productCount());
        assertEquals(3L, view.riskStockCount());
        assertEquals(3L, view.todayBillCount());
        assertEquals(new BigDecimal("1299.00"), view.todayActualAmount());
    }
}
