package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreSalesBill;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select bill from StoreSalesBill bill
            where (:customerPhone is null or bill.customerPhone = :customerPhone)
              and (:customerName is null or lower(bill.customerName) like lower(concat('%', :customerName, '%')))
              and (:billNo is null or lower(bill.billNo) = lower(:billNo))
              and (:startDate is null or bill.purchaseDate >= :startDate)
              and (:endDate is null or bill.purchaseDate <= :endDate)
            order by bill.purchaseDate desc, bill.createdAt desc
            """)
    List<StoreSalesBill> searchBills(@Param("customerPhone") String customerPhone,
                                     @Param("customerName") String customerName,
                                     @Param("billNo") String billNo,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     Pageable pageable);

    long countByPurchaseDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("""
            select coalesce(sum(b.actualAmount), 0)
            from StoreSalesBill b
            where b.purchaseDate between :startDate and :endDate
            """)
    BigDecimal sumActualAmountByPurchaseDateBetween(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);
}
