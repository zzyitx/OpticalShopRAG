package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreOutboundOrderRepository extends JpaRepository<StoreOutboundOrder, Long> {

    Optional<StoreOutboundOrder> findByOrderNo(String orderNo);
}
