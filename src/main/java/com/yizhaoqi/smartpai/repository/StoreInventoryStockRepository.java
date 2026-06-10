package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreInventoryStockRepository extends JpaRepository<StoreInventoryStock, Long> {

    Optional<StoreInventoryStock> findByProductSkuAndWarehouseCode(String productSku, String warehouseCode);

    long countByStatus(StoreInventoryStock.StockStatus status);
}
