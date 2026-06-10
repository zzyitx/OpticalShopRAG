package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理门店商品主数据、SKU 唯一性、可编辑属性和生命周期状态。
 */
@Service
public class StoreProductService {

    private final StoreProductRepository storeProductRepository;

    public StoreProductService(StoreProductRepository storeProductRepository) {
        this.storeProductRepository = storeProductRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductView> listProducts() {
        return storeProductRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductView getProduct(Long id) {
        return storeProductRepository.findById(id)
                .map(this::toView)
                .orElseThrow(() -> new CustomException("STORE_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ProductView createProduct(ProductCreateRequest request, String operator) {
        validateCreateRequest(request);
        String sku = normalizeSku(request.sku());

        // SKU 是库存和账单引用商品的业务键，创建前必须先阻止重复写入。
        if (storeProductRepository.findBySku(sku).isPresent()) {
            throw new CustomException("STORE_PRODUCT_SKU_EXISTS", HttpStatus.CONFLICT);
        }

        StoreProduct product = new StoreProduct();
        applyEditableFields(product, request, operator);
        product.setSku(sku);
        product.setStatus(StoreProduct.ProductStatus.ENABLED);
        product.setCreatedBy(operator);
        return toView(storeProductRepository.save(product));
    }

    @Transactional
    public ProductView updateProduct(Long id, ProductUpdateRequest request, String operator) {
        if (request == null) {
            throw new CustomException("STORE_PRODUCT_REQUEST_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        StoreProduct product = storeProductRepository.findById(id)
                .orElseThrow(() -> new CustomException("STORE_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
        applyEditableFields(product, request.toCreateRequest(product.getSku()), operator);
        return toView(storeProductRepository.save(product));
    }

    @Transactional
    public ProductView updateStatus(Long id, StoreProduct.ProductStatus status, String operator) {
        if (status == null) {
            throw new CustomException("STORE_PRODUCT_STATUS_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        StoreProduct product = storeProductRepository.findById(id)
                .orElseThrow(() -> new CustomException("STORE_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));

        // 状态会影响后续入库、出库和账单引用，这里只修改状态并保留商品历史主数据。
        product.setStatus(status);
        product.setUpdatedBy(operator);
        return toView(storeProductRepository.save(product));
    }

    private void validateCreateRequest(ProductCreateRequest request) {
        if (request == null) {
            throw new CustomException("STORE_PRODUCT_REQUEST_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.sku())) {
            throw new CustomException("STORE_PRODUCT_SKU_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.name())) {
            throw new CustomException("STORE_PRODUCT_NAME_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (request.category() == null) {
            throw new CustomException("STORE_PRODUCT_CATEGORY_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (request.unit() == null) {
            throw new CustomException("STORE_PRODUCT_UNIT_REQUIRED", HttpStatus.BAD_REQUEST);
        }
    }

    private void applyEditableFields(StoreProduct product, ProductCreateRequest request, String operator) {
        // 在服务边界统一清洗可选文本和数值默认值，避免不同入口写入不一致的商品数据。
        product.setName(request.name().trim());
        product.setCategory(request.category());
        product.setBrand(trimToNull(request.brand()));
        product.setModel(trimToNull(request.model()));
        product.setBarcode(trimToNull(request.barcode()));
        product.setSpecification(trimToNull(request.specification()));
        product.setColor(trimToNull(request.color()));
        product.setMaterial(trimToNull(request.material()));
        product.setUnit(request.unit());
        product.setPurchasePrice(defaultMoney(request.purchasePrice()));
        product.setRetailPrice(defaultMoney(request.retailPrice()));
        product.setSupplier(trimToNull(request.supplier()));
        product.setSafeStock(request.safeStock() == null ? 0 : Math.max(request.safeStock(), 0));
        product.setUpdatedBy(operator);
    }

    private ProductView toView(StoreProduct product) {
        return new ProductView(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getBarcode(),
                product.getSpecification(),
                product.getColor(),
                product.getMaterial(),
                product.getUnit(),
                product.getPurchasePrice(),
                product.getRetailPrice(),
                product.getSupplier(),
                product.getSafeStock(),
                product.getStatus(),
                product.getCreatedBy(),
                product.getUpdatedBy(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private String normalizeSku(String sku) {
        return sku.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ProductCreateRequest(
            String sku,
            String name,
            StoreProduct.ProductCategory category,
            String brand,
            String model,
            String barcode,
            String specification,
            String color,
            String material,
            StoreProduct.ProductUnit unit,
            BigDecimal purchasePrice,
            BigDecimal retailPrice,
            String supplier,
            Integer safeStock
    ) {
    }

    public record ProductUpdateRequest(
            String name,
            StoreProduct.ProductCategory category,
            String brand,
            String model,
            String barcode,
            String specification,
            String color,
            String material,
            StoreProduct.ProductUnit unit,
            BigDecimal purchasePrice,
            BigDecimal retailPrice,
            String supplier,
            Integer safeStock
    ) {
        private ProductCreateRequest toCreateRequest(String sku) {
            return new ProductCreateRequest(
                    sku,
                    name,
                    category,
                    brand,
                    model,
                    barcode,
                    specification,
                    color,
                    material,
                    unit,
                    purchasePrice,
                    retailPrice,
                    supplier,
                    safeStock
            );
        }
    }

    public record ProductStatusRequest(StoreProduct.ProductStatus status) {
    }

    public record ProductView(
            Long id,
            String sku,
            String name,
            StoreProduct.ProductCategory category,
            String brand,
            String model,
            String barcode,
            String specification,
            String color,
            String material,
            StoreProduct.ProductUnit unit,
            BigDecimal purchasePrice,
            BigDecimal retailPrice,
            String supplier,
            Integer safeStock,
            StoreProduct.ProductStatus status,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
