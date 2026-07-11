package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoreInboundOrderRepository extends JpaRepository<StoreInboundOrder, Long> {

    Optional<StoreInboundOrder> findByOrderNo(String orderNo);

    @Query("""
            select orders from StoreInboundOrder orders
            where (:orderNo is null or lower(orders.orderNo) = lower(:orderNo))
              and (:status is null or orders.status = :status)
              and (:startAt is null or orders.createdAt >= :startAt)
              and (:endAt is null or orders.createdAt < :endAt)
            order by orders.createdAt desc, orders.id desc
            """)
    List<StoreInboundOrder> searchOrders(@Param("orderNo") String orderNo,
                                         @Param("status") StoreInboundOrder.InboundStatus status,
                                         @Param("startAt") LocalDateTime startAt,
                                         @Param("endAt") LocalDateTime endAt,
                                         Pageable pageable);
}
