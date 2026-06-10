package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreSalesBill;
import com.yizhaoqi.smartpai.model.StoreSalesBillChangeLog;
import com.yizhaoqi.smartpai.repository.StoreSalesBillChangeLogRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreSalesBillServiceTest {

    private StoreSalesBillRepository salesBillRepository;
    private StoreSalesBillChangeLogRepository changeLogRepository;
    private StoreSalesBillService service;

    @BeforeEach
    void setUp() {
        salesBillRepository = Mockito.mock(StoreSalesBillRepository.class);
        changeLogRepository = Mockito.mock(StoreSalesBillChangeLogRepository.class);
        service = new StoreSalesBillService(salesBillRepository, changeLogRepository);
    }

    @Test
    void shouldCreateSeparateBillsForSameCustomerPhone() {
        when(salesBillRepository.save(any(StoreSalesBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreSalesBillService.SalesBillCreateRequest first = createRequest("张三", "13800000000", LocalDate.of(2026, 6, 1));
        StoreSalesBillService.SalesBillCreateRequest second = createRequest("张三", "13800000000", LocalDate.of(2026, 6, 8));

        StoreSalesBillService.SalesBillView firstView = service.createBill(first, "admin");
        StoreSalesBillService.SalesBillView secondView = service.createBill(second, "admin");

        assertEquals("13800000000", firstView.customerPhone());
        assertEquals("13800000000", secondView.customerPhone());
        verify(salesBillRepository, times(2)).save(any(StoreSalesBill.class));
    }

    @Test
    void shouldReturnCustomerHistoryWithoutOverwritingOlderBill() {
        StoreSalesBill older = new StoreSalesBill();
        older.setBillNo("SB-OLD");
        older.setCustomerPhone("13800000000");
        older.setPurchaseDate(LocalDate.of(2026, 6, 1));

        StoreSalesBill newer = new StoreSalesBill();
        newer.setBillNo("SB-NEW");
        newer.setCustomerPhone("13800000000");
        newer.setPurchaseDate(LocalDate.of(2026, 6, 8));

        when(salesBillRepository.findByCustomerPhoneOrderByPurchaseDateDescCreatedAtDesc("13800000000"))
                .thenReturn(List.of(newer, older));

        List<StoreSalesBillService.SalesBillView> history = service.getCustomerHistory("13800000000");

        assertEquals(2, history.size());
        assertEquals("SB-NEW", history.get(0).billNo());
        assertEquals("SB-OLD", history.get(1).billNo());
    }

    @Test
    void shouldWriteChangeLogWhenUpdatingBill() {
        StoreSalesBill bill = new StoreSalesBill();
        bill.setId(1L);
        bill.setBillNo("SB-001");
        bill.setCustomerName("张三");
        bill.setCustomerPhone("13800000000");
        bill.setPaymentAmount(new BigDecimal("399.00"));

        StoreSalesBillService.SalesBillUpdateRequest request = new StoreSalesBillService.SalesBillUpdateRequest(
                "张三",
                "13800000000",
                LocalDate.of(2026, 6, 8),
                new BigDecimal("-3.50"),
                new BigDecimal("-0.75"),
                180,
                new BigDecimal("-2.75"),
                new BigDecimal("-0.50"),
                170,
                new BigDecimal("62.00"),
                "FRAME-A123",
                "LENS-B456",
                new BigDecimal("499.00"),
                new BigDecimal("50.00"),
                new BigDecimal("449.00"),
                StoreSalesBill.PaymentMethod.WECHAT,
                "李店员",
                "王验光师",
                "复配"
        );

        when(salesBillRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(salesBillRepository.save(any(StoreSalesBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreSalesBillService.SalesBillView view = service.updateBill(1L, request, "admin");

        assertEquals(new BigDecimal("499.00"), view.paymentAmount());
        verify(changeLogRepository).save(any(StoreSalesBillChangeLog.class));
    }

    @Test
    void shouldRejectBillWithoutCustomerPhone() {
        StoreSalesBillService.SalesBillCreateRequest request = createRequest("张三", "", LocalDate.now());

        CustomException exception = assertThrows(CustomException.class,
                () -> service.createBill(request, "admin"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("STORE_SALES_BILL_PHONE_REQUIRED", exception.getMessage());
    }

    private StoreSalesBillService.SalesBillCreateRequest createRequest(String customerName, String phone, LocalDate purchaseDate) {
        return new StoreSalesBillService.SalesBillCreateRequest(
                customerName,
                phone,
                purchaseDate,
                new BigDecimal("-3.50"),
                new BigDecimal("-0.75"),
                180,
                new BigDecimal("-2.75"),
                new BigDecimal("-0.50"),
                170,
                new BigDecimal("62.00"),
                "FRAME-A123",
                "LENS-B456",
                new BigDecimal("399.00"),
                BigDecimal.ZERO,
                new BigDecimal("399.00"),
                StoreSalesBill.PaymentMethod.CASH,
                "李店员",
                "王验光师",
                ""
        );
    }
}
