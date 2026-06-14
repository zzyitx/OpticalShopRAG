package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.StoreProductService;
import com.yizhaoqi.smartpai.service.StoreOperatorResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供门店经营台所需的商品主数据接口。
 */
@RestController
@RequestMapping("/api/v1/store/products")
public class StoreProductController {

    private final StoreProductService storeProductService;
    private final StoreOperatorResolver operatorResolver;

    public StoreProductController(StoreProductService storeProductService,
                                  StoreOperatorResolver operatorResolver) {
        this.storeProductService = storeProductService;
        this.operatorResolver = operatorResolver;
    }

    @GetMapping
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.product.view')")
    public ResponseEntity<?> listProducts() {
        return ResponseEntity.ok(success(storeProductService.listProducts()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.product.view')")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(success(storeProductService.getProduct(id)));
    }

    @PostMapping
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.product.create')")
    public ResponseEntity<?> createProduct(
            @RequestBody StoreProductService.ProductCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeProductService.createProduct(request, operatorResolver.resolve(authentication))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.product.update')")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestBody StoreProductService.ProductUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeProductService.updateProduct(id, request, operatorResolver.resolve(authentication))));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.product.update')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody StoreProductService.ProductStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeProductService.updateStatus(id, request.status(), operatorResolver.resolve(authentication))));
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
