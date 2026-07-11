package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreInboundItem;
import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreOutboundItem;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.repository.StoreInboundOrderRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryLedgerRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreOutboundOrderRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreInventoryServiceTest {

    private StoreInventoryStockRepository stockRepository;
    private StoreInventoryLedgerRepository ledgerRepository;
    private StoreInboundOrderRepository inboundOrderRepository;
    private StoreOutboundOrderRepository outboundOrderRepository;
    private StoreProductRepository productRepository;
    private StoreInventoryService service;

    @BeforeEach
    void setUp() {
        stockRepository = Mockito.mock(StoreInventoryStockRepository.class);
        ledgerRepository = Mockito.mock(StoreInventoryLedgerRepository.class);
        inboundOrderRepository = Mockito.mock(StoreInboundOrderRepository.class);
        outboundOrderRepository = Mockito.mock(StoreOutboundOrderRepository.class);
        productRepository = Mockito.mock(StoreProductRepository.class);
        service = new StoreInventoryService(
                stockRepository,
                ledgerRepository,
                inboundOrderRepository,
                outboundOrderRepository,
                productRepository
        );
    }

    @Test
    void shouldCreateInboundDraftOrderWithItems() {
        when(inboundOrderRepository.save(any(StoreInboundOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findBySku("FRAME-A123")).thenReturn(Optional.of(enabledProduct("FRAME-A123")));

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
    void shouldRejectInboundForUnknownProductSku() {
        when(productRepository.findBySku("UNKNOWN")).thenReturn(Optional.empty());
        StoreInventoryService.InboundOrderCreateRequest request = new StoreInventoryService.InboundOrderCreateRequest(
                StoreInboundOrder.InboundType.PURCHASE,
                "默认供应商",
                null,
                List.of(new StoreInventoryService.InboundItemRequest("UNKNOWN", null, 1, BigDecimal.TEN))
        );

        CustomException exception = assertThrows(CustomException.class, () -> service.createInbound(request, "admin"));

        assertEquals("STORE_PRODUCT_NOT_FOUND", exception.getMessage());
        verify(inboundOrderRepository, never()).save(any());
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
        when(stockRepository.findByProductSkuAndWarehouseCodeForUpdate("FRAME-A123", StoreInventoryService.DEFAULT_WAREHOUSE_CODE))
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
        when(stockRepository.findByProductSkuAndWarehouseCodeForUpdate("FRAME-A123", StoreInventoryService.DEFAULT_WAREHOUSE_CODE))
                .thenReturn(Optional.of(stock));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.confirmOutbound(2L, "admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STORE_INVENTORY_NOT_ENOUGH", exception.getMessage());
        verify(stockRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void shouldCancelDraftInboundWithoutChangingStock() {
        StoreInboundOrder order = new StoreInboundOrder();
        order.setId(3L);
        order.setOrderNo("IN-003");
        order.setStatus(StoreInboundOrder.InboundStatus.DRAFT);
        when(inboundOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(inboundOrderRepository.save(any(StoreInboundOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreInventoryService.InboundOrderView view = service.cancelInbound(3L, "admin");

        assertEquals(StoreInboundOrder.InboundStatus.CANCELLED, view.status());
        verify(stockRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void shouldListStocksWithBoundedPageRequest() {
        StoreInventoryStock stock = new StoreInventoryStock();
        stock.setId(10L);
        stock.setProductSku("FRAME-A123");
        stock.setWarehouseCode(StoreInventoryService.DEFAULT_WAREHOUSE_CODE);
        stock.setCurrentQuantity(3);
        stock.setAvailableQuantity(3);
        stock.setSafeStock(5);
        stock.setStatus(StoreInventoryStock.StockStatus.LOW_STOCK);
        when(stockRepository.searchStocks(
                eq("FRAME-A123"),
                eq(StoreInventoryStock.StockStatus.LOW_STOCK),
                eq(false),
                eq(PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE))
        ))
                .thenReturn(List.of(stock));

        List<StoreInventoryService.StockView> result = service.listStocks(
                new StoreInventoryService.StockListQuery(" FRAME-A123 ", StoreInventoryStock.StockStatus.LOW_STOCK, 0, 200)
        );

        assertEquals(1, result.size());
        assertEquals("FRAME-A123", result.get(0).productSku());
        verify(stockRepository).searchStocks(
                "FRAME-A123",
                StoreInventoryStock.StockStatus.LOW_STOCK,
                false,
                PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE)
        );
    }

    @Test
    void shouldListInboundOrdersWithBoundedFilters() {
        StoreInboundOrder order = new StoreInboundOrder();
        order.setId(11L);
        order.setOrderNo("IN-001");
        order.setStatus(StoreInboundOrder.InboundStatus.DRAFT);
        when(inboundOrderRepository.searchOrders(
                eq("IN-001"),
                eq(StoreInboundOrder.InboundStatus.DRAFT),
                eq(LocalDate.of(2026, 6, 1).atStartOfDay()),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE))
        )).thenReturn(List.of(order));

        List<StoreInventoryService.InboundOrderView> result = service.listInbounds(
                new StoreInventoryService.InboundOrderListQuery(
                        "IN-001",
                        StoreInboundOrder.InboundStatus.DRAFT,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        0,
                        200
                )
        );

        assertEquals(1, result.size());
        assertEquals("IN-001", result.get(0).orderNo());
        verify(inboundOrderRepository).searchOrders(
                "IN-001",
                StoreInboundOrder.InboundStatus.DRAFT,
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE)
        );
    }

    @Test
    void shouldListOutboundOrdersWithBoundedFilters() {
        StoreOutboundOrder order = new StoreOutboundOrder();
        order.setId(12L);
        order.setOrderNo("OUT-001");
        order.setStatus(StoreOutboundOrder.OutboundStatus.CONFIRMED);
        when(outboundOrderRepository.searchOrders(
                eq("OUT-001"),
                eq(StoreOutboundOrder.OutboundStatus.CONFIRMED),
                eq(LocalDate.of(2026, 6, 1).atStartOfDay()),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE))
        )).thenReturn(List.of(order));

        List<StoreInventoryService.OutboundOrderView> result = service.listOutbounds(
                new StoreInventoryService.OutboundOrderListQuery(
                        "OUT-001",
                        StoreOutboundOrder.OutboundStatus.CONFIRMED,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        0,
                        200
                )
        );

        assertEquals(1, result.size());
        assertEquals("OUT-001", result.get(0).orderNo());
        verify(outboundOrderRepository).searchOrders(
                "OUT-001",
                StoreOutboundOrder.OutboundStatus.CONFIRMED,
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE)
        );
    }

    @Test
    void shouldListLedgersWithBoundedFilters() {
        StoreInventoryLedger ledger = new StoreInventoryLedger();
        ledger.setId(13L);
        ledger.setProductSku("FRAME-A123");
        ledger.setBusinessOrderNo("IN-001");
        ledger.setChangeType(StoreInventoryLedger.ChangeType.INBOUND);
        ledger.setQuantityBefore(0);
        ledger.setChangeQuantity(5);
        ledger.setQuantityAfter(5);
        ledger.setOperationSource(StoreInventoryLedger.OperationSource.INBOUND_ORDER);
        ledger.setOperatedAt(LocalDateTime.of(2026, 6, 8, 10, 0));
        when(ledgerRepository.searchLedgers(
                eq("FRAME-A123"),
                eq("IN-001"),
                eq(StoreInventoryLedger.ChangeType.INBOUND),
                eq(LocalDate.of(2026, 6, 1).atStartOfDay()),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE))
        )).thenReturn(List.of(ledger));

        List<StoreInventoryService.LedgerView> result = service.listLedgers(
                new StoreInventoryService.LedgerListQuery(
                        "FRAME-A123",
                        "IN-001",
                        StoreInventoryLedger.ChangeType.INBOUND,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        0,
                        200
                )
        );

        assertEquals(1, result.size());
        assertEquals("FRAME-A123", result.get(0).productSku());
        verify(ledgerRepository).searchLedgers(
                "FRAME-A123",
                "IN-001",
                StoreInventoryLedger.ChangeType.INBOUND,
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                PageRequest.of(0, StoreInventoryService.MAX_LIST_SIZE)
        );
    }

    @Test
    void shouldUseDefaultBoundedPageRequestForStockList() {
        when(stockRepository.searchStocks(
                eq(null),
                eq(null),
                eq(false),
                eq(PageRequest.of(0, StoreInventoryService.DEFAULT_LIST_SIZE))
        )).thenReturn(List.of());

        service.listStocks();

        verify(stockRepository).searchStocks(
                null,
                null,
                false,
                PageRequest.of(0, StoreInventoryService.DEFAULT_LIST_SIZE)
        );
    }

    @Test
    void shouldNormalizeInvalidPageAndSizeForLedgerList() {
        when(ledgerRepository.searchLedgers(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, StoreInventoryService.DEFAULT_LIST_SIZE))
        )).thenReturn(List.of());

        service.listLedgers(new StoreInventoryService.LedgerListQuery(
                " ",
                " ",
                null,
                null,
                null,
                -1,
                0
        ));

        verify(ledgerRepository).searchLedgers(
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, StoreInventoryService.DEFAULT_LIST_SIZE)
        );
    }

    private StoreProduct enabledProduct(String sku) {
        StoreProduct product = new StoreProduct();
        product.setSku(sku);
        product.setName("测试商品");
        product.setStatus(StoreProduct.ProductStatus.ENABLED);
        return product;
    }
}
