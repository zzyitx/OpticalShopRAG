package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 聚合门店看板展示的商品、库存风险和当日销售核心指标。
 */
@Service
public class StoreDashboardService {

    private final StoreProductRepository productRepository;
    private final StoreInventoryStockRepository stockRepository;
    private final StoreSalesBillRepository salesBillRepository;

    public StoreDashboardService(StoreProductRepository productRepository,
                                 StoreInventoryStockRepository stockRepository,
                                 StoreSalesBillRepository salesBillRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.salesBillRepository = salesBillRepository;
    }

    @Transactional(readOnly = true)
    public StoreDashboardView getSummary() {
        LocalDate today = LocalDate.now();
        long lowStockCount = stockRepository.countByStatus(StoreInventoryStock.StockStatus.LOW_STOCK);
        long outOfStockCount = stockRepository.countByStatus(StoreInventoryStock.StockStatus.OUT_OF_STOCK);
        BigDecimal todayActualAmount = salesBillRepository.sumActualAmountByPurchaseDate(today);

        // 阶段一看板只展示单店经营闭环所需的核心指标，库存风险由低库存和无库存 SKU 合并计算。
        return new StoreDashboardView(
                productRepository.count(),
                lowStockCount + outOfStockCount,
                salesBillRepository.countByPurchaseDate(today),
                todayActualAmount == null ? BigDecimal.ZERO : todayActualAmount
        );
    }

    public record StoreDashboardView(
            long productCount,
            long riskStockCount,
            long todayBillCount,
            BigDecimal todayActualAmount
    ) {
    }
}
