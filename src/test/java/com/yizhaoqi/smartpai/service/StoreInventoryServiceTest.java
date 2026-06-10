package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreInboundItem;
import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreOutboundItem;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.repository.StoreInboundOrderRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryLedgerRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreOutboundOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreInventoryServiceTest {

    private StoreInventoryStockRepository stockRepository;
    private StoreInventoryLedgerRepository ledgerRepository;
    private StoreInboundOrderRepository inboundOrderRepository;
    private StoreOutboundOrderRepository outboundOrderRepository;
    private StoreInventoryService service;

    @BeforeEach
    void setUp() {
        stockRepository = Mockito.mock(StoreInventoryStockRepository.class);
        ledgerRepository = Mockito.mock(StoreInventoryLedgerRepository.class);
        inboundOrderRepository = Mockito.mock(StoreInboundOrderRepository.class);
        outboundOrderRepository = Mockito.mock(StoreOutboundOrderRepository.class);
        service = new StoreInventoryService(stockRepository, ledgerRepository, inboundOrderRepository, outboundOrderRepository);
    }

    @Test
    void shouldCreateInboundDraftOrderWithItems() {
        when(inboundOrderRepository.save(any(StoreInboundOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreInventoryService.InboundOrderCreateRequest request = new StoreInventoryService.InboundOrderCreateRequest(
                StoreInboundOrder.InboundType.PURCHASE,
                "默认供应商",
                "采购到货",
                List.of(new StoreInventoryService.InboundItemRequest(
                        "FRAME-A123",
                        "儿童防蓝光镜框 A123",
                        5,
                        new BigDecimal("120.00")
                ))
        );

        StoreInventoryService.InboundOrderView view = service.createInbound(request, "admin");

        assertEquals(StoreInboundOrder.InboundStatus.DRAFT, view.status());
        verify(inboundOrderRepository).save(any(StoreInboundOrder.class));
    }

    @Test
    void shouldIncreaseStockAndWriteLedgerWhenConfirmingInboundOrder() {
        StoreInboundOrder order = new StoreInboundOrder();
        order.setId(1L);
        order.setOrderNo("IN-001");
        order.setStatus(StoreInboundOrder.InboundStatus.DRAFT);
        order.setItems(new ArrayList<>());

        StoreInboundItem item = new StoreInboundItem();
        item.setProductSku("FRAME-A123");
        item.setProductNameSnapshot("儿童防蓝光镜框 A123");
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("120.00"));
        order.getItems().add(item);

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stockRepository.findByProductSkuAndWarehouseCode("FRAME-A123", StoreInventoryService.DEFAULT_WAREHOUSE_CODE))
                .thenReturn(Optional.empty());
        when(stockRepository.save(any(StoreInventoryStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreInventoryService.InboundOrderView view = service.confirmInbound(1L, "admin");

        assertEquals(StoreInboundOrder.InboundStatus.CONFIRMED, order.getStatus());
        assertEquals(StoreInboundOrder.InboundStatus.CONFIRMED, view.status());
        verify(stockRepository).save(any(StoreInventoryStock.class));
        verify(ledgerRepository).save(any());
    }

    @Test
    void shouldRejectOutboundAndSkipLedgerWhenStockIsInsufficient() {
        StoreOutboundOrder order = new StoreOutboundOrder();
        order.setId(2L);
        order.setOrderNo("OUT-001");
        order.setStatus(StoreOutboundOrder.OutboundStatus.DRAFT);
        order.setItems(new ArrayList<>());

        StoreOutboundItem item = new StoreOutboundItem();
        item.setProductSku("FRAME-A123");
        item.setProductNameSnapshot("儿童防蓝光镜框 A123");
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("399.00"));
        order.getItems().add(item);

        StoreInventoryStock stock = new StoreInventoryStock();
        stock.setProductSku("FRAME-A123");
        stock.setWarehouseCode(StoreInventoryService.DEFAULT_WAREHOUSE_CODE);
        stock.setCurrentQuantity(2);

        when(outboundOrderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(stockRepository.findByProductSkuAndWarehouseCode("FRAME-A123", StoreInventoryService.DEFAULT_WAREHOUSE_CODE))
                .thenReturn(Optional.of(stock));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.confirmOutbound(2L, "admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STORE_INVENTORY_NOT_ENOUGH", exception.getMessage());
        verify(stockRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
}
