package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreInventoryLedgerRepository extends JpaRepository<StoreInventoryLedger, Long> {

    List<StoreInventoryLedger> findByProductSkuOrderByOperatedAtDesc(String productSku);
}
