package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreInventoryStockRepository extends JpaRepository<StoreInventoryStock, Long> {

    Optional<StoreInventoryStock> findByProductSkuAndWarehouseCode(String productSku, String warehouseCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select stock from StoreInventoryStock stock
            where stock.productSku = :productSku and stock.warehouseCode = :warehouseCode
            """)
    Optional<StoreInventoryStock> findByProductSkuAndWarehouseCodeForUpdate(
            @Param("productSku") String productSku,
            @Param("warehouseCode") String warehouseCode
    );

    long countByStatus(StoreInventoryStock.StockStatus status);

    @Query("""
            select stock from StoreInventoryStock stock
            where (:productSku is null or lower(stock.productSku) = lower(:productSku))
              and (:status is null or stock.status = :status)
              and (
                :warningOnly = false
                or stock.status in (
                    com.yizhaoqi.smartpai.model.StoreInventoryStock.StockStatus.LOW_STOCK,
                    com.yizhaoqi.smartpai.model.StoreInventoryStock.StockStatus.OUT_OF_STOCK
                )
              )
            order by stock.status asc, stock.currentQuantity asc, stock.updatedAt desc
            """)
    List<StoreInventoryStock> searchStocks(@Param("productSku") String productSku,
                                           @Param("status") StoreInventoryStock.StockStatus status,
                                           @Param("warningOnly") boolean warningOnly,
                                           Pageable pageable);
}
