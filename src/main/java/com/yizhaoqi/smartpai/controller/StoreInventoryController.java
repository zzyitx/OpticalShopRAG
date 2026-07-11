package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.service.StoreInventoryService;
import com.yizhaoqi.smartpai.service.StoreOperatorResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 提供库存查询以及入库单、出库单的草稿和确认流程接口。
 */
@RestController
@RequestMapping("/api/v1/store/inventory")
public class StoreInventoryController {

    private final StoreInventoryService storeInventoryService;
    private final StoreOperatorResolver operatorResolver;

    public StoreInventoryController(StoreInventoryService storeInventoryService,
                                    StoreOperatorResolver operatorResolver) {
        this.storeInventoryService = storeInventoryService;
        this.operatorResolver = operatorResolver;
    }

    @GetMapping("/stocks")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.view')")
    public ResponseEntity<?> listStocks(@RequestParam(required = false) String productSku,
                                        @RequestParam(required = false) StoreInventoryStock.StockStatus status,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(success(storeInventoryService.listStocks(
                new StoreInventoryService.StockListQuery(productSku, status, page, size)
        )));
    }

    @GetMapping("/inbounds")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.view')")
    public ResponseEntity<?> listInbounds(@RequestParam(required = false) String orderNo,
                                          @RequestParam(required = false) StoreInboundOrder.InboundStatus status,
                                          @RequestParam(required = false) LocalDate startDate,
                                          @RequestParam(required = false) LocalDate endDate,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(success(storeInventoryService.listInbounds(
                new StoreInventoryService.InboundOrderListQuery(orderNo, status, startDate, endDate, page, size)
        )));
    }

    @GetMapping("/outbounds")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.view')")
    public ResponseEntity<?> listOutbounds(@RequestParam(required = false) String orderNo,
                                           @RequestParam(required = false) StoreOutboundOrder.OutboundStatus status,
                                           @RequestParam(required = false) LocalDate startDate,
                                           @RequestParam(required = false) LocalDate endDate,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(success(storeInventoryService.listOutbounds(
                new StoreInventoryService.OutboundOrderListQuery(orderNo, status, startDate, endDate, page, size)
        )));
    }

    @GetMapping("/ledgers")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.view')")
    public ResponseEntity<?> listLedgers(@RequestParam(required = false) String productSku,
                                         @RequestParam(required = false) String businessOrderNo,
                                         @RequestParam(required = false) StoreInventoryLedger.ChangeType changeType,
                                         @RequestParam(required = false) LocalDate startDate,
                                         @RequestParam(required = false) LocalDate endDate,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(success(storeInventoryService.listLedgers(
                new StoreInventoryService.LedgerListQuery(
                        productSku,
                        businessOrderNo,
                        changeType,
                        startDate,
                        endDate,
                        page,
                        size
                )
        )));
    }

    @PostMapping("/inbounds")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.inbound.create')")
    public ResponseEntity<?> createInbound(
            @RequestBody StoreInventoryService.InboundOrderCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.createInbound(request, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/inbounds/{id}/confirm")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.inbound.create')")
    public ResponseEntity<?> confirmInbound(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.confirmInbound(id, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/inbounds/{id}/cancel")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.inbound.create')")
    public ResponseEntity<?> cancelInbound(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(success(storeInventoryService.cancelInbound(id, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/outbounds")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.outbound.create')")
    public ResponseEntity<?> createOutbound(
            @RequestBody StoreInventoryService.OutboundOrderCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.createOutbound(request, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/outbounds/{id}/confirm")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.outbound.create')")
    public ResponseEntity<?> confirmOutbound(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.confirmOutbound(id, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/outbounds/{id}/cancel")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.inventory.outbound.create')")
    public ResponseEntity<?> cancelOutbound(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(success(storeInventoryService.cancelOutbound(id, operatorResolver.resolve(authentication))));
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
