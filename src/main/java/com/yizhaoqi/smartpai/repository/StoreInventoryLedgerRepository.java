package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StoreInventoryLedgerRepository extends JpaRepository<StoreInventoryLedger, Long> {

    List<StoreInventoryLedger> findByProductSkuOrderByOperatedAtDesc(String productSku);

    @Query("""
            select ledger from StoreInventoryLedger ledger
            where (:productSku is null or lower(ledger.productSku) = lower(:productSku))
              and (:businessOrderNo is null or lower(ledger.businessOrderNo) = lower(:businessOrderNo))
              and (:changeType is null or ledger.changeType = :changeType)
              and (:startAt is null or ledger.operatedAt >= :startAt)
              and (:endAt is null or ledger.operatedAt <= :endAt)
            order by ledger.operatedAt desc, ledger.id desc
            """)
    List<StoreInventoryLedger> searchLedgers(@Param("productSku") String productSku,
                                             @Param("businessOrderNo") String businessOrderNo,
                                             @Param("changeType") StoreInventoryLedger.ChangeType changeType,
                                             @Param("startAt") LocalDateTime startAt,
                                             @Param("endAt") LocalDateTime endAt,
                                             Pageable pageable);
}
