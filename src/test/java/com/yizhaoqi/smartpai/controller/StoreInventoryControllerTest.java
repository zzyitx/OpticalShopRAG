package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.service.StoreInventoryService;
import com.yizhaoqi.smartpai.service.StoreOperatorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreInventoryControllerTest {

    private StoreInventoryService inventoryService;
    private StoreInventoryController controller;

    @BeforeEach
    void setUp() {
        inventoryService = Mockito.mock(StoreInventoryService.class);
        controller = new StoreInventoryController(
                inventoryService,
                Mockito.mock(StoreOperatorResolver.class)
        );
    }

    @Test
    void shouldPassStockFiltersAndKeepArrayResponse() {
        when(inventoryService.listStocks(any(StoreInventoryService.StockListQuery.class))).thenReturn(List.of());

        ResponseEntity<?> response = controller.listStocks(
                "FRAME-A123",
                StoreInventoryStock.StockStatus.LOW_STOCK,
                2,
                50
        );

        verify(inventoryService).listStocks(new StoreInventoryService.StockListQuery(
                "FRAME-A123",
                StoreInventoryStock.StockStatus.LOW_STOCK,
                2,
                50
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        assertEquals(List.of(), body.get("data"));
    }

    @Test
    void shouldPassInboundOrderFilters() {
        when(inventoryService.listInbounds(any(StoreInventoryService.InboundOrderListQuery.class))).thenReturn(List.of());
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);

        controller.listInbounds("IN-001", StoreInboundOrder.InboundStatus.DRAFT, startDate, endDate, 1, 25);

        verify(inventoryService).listInbounds(new StoreInventoryService.InboundOrderListQuery(
                "IN-001",
                StoreInboundOrder.InboundStatus.DRAFT,
                startDate,
                endDate,
                1,
                25
        ));
    }

    @Test
    void shouldPassOutboundOrderFilters() {
        when(inventoryService.listOutbounds(any(StoreInventoryService.OutboundOrderListQuery.class))).thenReturn(List.of());
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);

        controller.listOutbounds("OUT-001", StoreOutboundOrder.OutboundStatus.CONFIRMED, startDate, endDate, 1, 25);

        verify(inventoryService).listOutbounds(new StoreInventoryService.OutboundOrderListQuery(
                "OUT-001",
                StoreOutboundOrder.OutboundStatus.CONFIRMED,
                startDate,
                endDate,
                1,
                25
        ));
    }

    @Test
    void shouldPassLedgerFilters() {
        when(inventoryService.listLedgers(any(StoreInventoryService.LedgerListQuery.class))).thenReturn(List.of());
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);

        controller.listLedgers(
                "FRAME-A123",
                "IN-001",
                StoreInventoryLedger.ChangeType.INBOUND,
                startDate,
                endDate,
                1,
                25
        );

        verify(inventoryService).listLedgers(new StoreInventoryService.LedgerListQuery(
                "FRAME-A123",
                "IN-001",
                StoreInventoryLedger.ChangeType.INBOUND,
                startDate,
                endDate,
                1,
                25
        ));
    }
}
