package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreInboundOrderRepository extends JpaRepository<StoreInboundOrder, Long> {

    Optional<StoreInboundOrder> findByOrderNo(String orderNo);
}
