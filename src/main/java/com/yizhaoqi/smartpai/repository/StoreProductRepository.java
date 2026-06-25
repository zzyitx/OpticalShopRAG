package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.StoreProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    Optional<StoreProduct> findBySku(String sku);

    List<StoreProduct> findBySkuIn(Collection<String> skuList);

    boolean existsBySku(String sku);

    @Query("""
            select product from StoreProduct product
            where (:sku is null or lower(product.sku) = lower(:sku))
              and (
                :keyword is null
                or lower(product.sku) like lower(concat('%', :keyword, '%'))
                or lower(product.name) like lower(concat('%', :keyword, '%'))
                or lower(product.brand) like lower(concat('%', :keyword, '%'))
                or lower(product.model) like lower(concat('%', :keyword, '%'))
              )
              and (:brand is null or lower(product.brand) like lower(concat('%', :brand, '%')))
              and (:model is null or lower(product.model) like lower(concat('%', :model, '%')))
              and (:category is null or product.category = :category)
            order by product.updatedAt desc, product.id desc
            """)
    List<StoreProduct> searchProducts(@Param("sku") String sku,
                                      @Param("keyword") String keyword,
                                      @Param("brand") String brand,
                                      @Param("model") String model,
                                      @Param("category") StoreProduct.ProductCategory category,
                                      Pageable pageable);
}
