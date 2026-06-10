package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.StoreInventoryService;
import com.yizhaoqi.smartpai.service.StoreOperatorResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> listStocks() {
        return ResponseEntity.ok(success(storeInventoryService.listStocks()));
    }

    @PostMapping("/inbounds")
    public ResponseEntity<?> createInbound(
            @RequestBody StoreInventoryService.InboundOrderCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.createInbound(request, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/inbounds/{id}/confirm")
    public ResponseEntity<?> confirmInbound(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.confirmInbound(id, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/outbounds")
    public ResponseEntity<?> createOutbound(
            @RequestBody StoreInventoryService.OutboundOrderCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.createOutbound(request, operatorResolver.resolve(authentication))));
    }

    @PostMapping("/outbounds/{id}/confirm")
    public ResponseEntity<?> confirmOutbound(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeInventoryService.confirmOutbound(id, operatorResolver.resolve(authentication))));
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
