package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreProductServiceTest {

    private StoreProductRepository repository;
    private StoreProductService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(StoreProductRepository.class);
        service = new StoreProductService(repository);
    }

    @Test
    void shouldCreateProductWithUniqueSku() {
        StoreProductService.ProductCreateRequest request = new StoreProductService.ProductCreateRequest(
                "FRAME-A123",
                "儿童防蓝光镜框 A123",
                StoreProduct.ProductCategory.FRAME,
                "明月",
                "A123",
                "",
                "儿童款 TR90",
                "黑色",
                "TR90",
                StoreProduct.ProductUnit.PAIR,
                new BigDecimal("120.00"),
                new BigDecimal("399.00"),
                "默认供应商",
                3
        );

        when(repository.findBySku("FRAME-A123")).thenReturn(Optional.empty());
        when(repository.save(any(StoreProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoreProductService.ProductView product = service.createProduct(request, "admin");

        assertEquals("FRAME-A123", product.sku());
        assertEquals("儿童防蓝光镜框 A123", product.name());
        assertEquals(StoreProduct.ProductStatus.ENABLED, product.status());
        verify(repository).save(any(StoreProduct.class));
    }

    @Test
    void shouldRejectDuplicateSkuWhenCreatingProduct() {
        StoreProduct existing = new StoreProduct();
        existing.setSku("FRAME-A123");

        StoreProductService.ProductCreateRequest request = new StoreProductService.ProductCreateRequest(
                "FRAME-A123",
                "重复 SKU 商品",
                StoreProduct.ProductCategory.FRAME,
                null,
                null,
                null,
                null,
                null,
                null,
                StoreProduct.ProductUnit.PAIR,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                0
        );

        when(repository.findBySku("FRAME-A123")).thenReturn(Optional.of(existing));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.createProduct(request, "admin"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STORE_PRODUCT_SKU_EXISTS", exception.getMessage());
    }
}
