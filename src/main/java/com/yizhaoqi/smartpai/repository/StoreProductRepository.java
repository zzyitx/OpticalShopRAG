package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    Optional<StoreProduct> findBySku(String sku);

    boolean existsBySku(String sku);
}
