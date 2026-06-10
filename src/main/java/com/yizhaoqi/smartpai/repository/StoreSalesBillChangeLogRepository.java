package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreSalesBillChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreSalesBillChangeLogRepository extends JpaRepository<StoreSalesBillChangeLog, Long> {

    List<StoreSalesBillChangeLog> findByBillIdOrderByChangedAtDesc(Long billId);
}
