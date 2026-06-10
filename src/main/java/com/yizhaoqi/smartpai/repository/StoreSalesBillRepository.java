package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreSalesBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreSalesBillRepository extends JpaRepository<StoreSalesBill, Long> {

    Optional<StoreSalesBill> findByBillNo(String billNo);

    List<StoreSalesBill> findAllByOrderByPurchaseDateDescCreatedAtDesc();

    List<StoreSalesBill> findByCustomerPhoneOrderByPurchaseDateDescCreatedAtDesc(String customerPhone);

    long countByPurchaseDate(LocalDate purchaseDate);

    @Query("select coalesce(sum(b.actualAmount), 0) from StoreSalesBill b where b.purchaseDate = :purchaseDate")
    BigDecimal sumActualAmountByPurchaseDate(@Param("purchaseDate") LocalDate purchaseDate);
}
