package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 眼镜店商品档案，作为库存、入库、出库和账单明细引用商品的权威主数据。
 */
@Data
@Entity
@Comment("眼镜店商品档案表，保存镜框、镜片、护理液、配件等可售或可入库商品的主数据")
@Table(
        name = "store_product",
        uniqueConstraints = @UniqueConstraint(name = "uk_store_product_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_store_product_category", columnList = "category"),
                @Index(name = "idx_store_product_status", columnList = "status")
        }
)
public class StoreProduct {

    /** 商品档案自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("商品档案自增主键，仅用于系统内部关联")
    private Long id;

    /** 商品唯一 SKU，入库、出库、账单和 AI 查询均以此识别商品。 */
    @Column(nullable = false, unique = true, length = 64)
    @Comment("商品唯一 SKU，入库、出库、账单和 AI 查询均以此识别商品")
    private String sku;

    /** 商品名称，展示给店员和客户的可读名称。 */
    @Column(nullable = false, length = 128)
    @Comment("商品名称，展示给店员和客户的可读名称")
    private String name;

    /** 商品分类，限定为镜框、镜片、太阳镜、隐形眼镜、护理液、配件或其他。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("商品分类：FRAME 镜框，LENS 镜片，SUNGLASSES 太阳镜，CONTACT_LENS 隐形眼镜，CARE_SOLUTION 护理液，ACCESSORY 配件，OTHER 其他")
    private ProductCategory category;

    /** 商品品牌，用于列表筛选和商品说明匹配。 */
    @Column(length = 128)
    @Comment("商品品牌，用于列表筛选和商品说明匹配")
    private String brand;

    /** 厂商或门店内部型号，和 SKU 不同，可重复。 */
    @Column(length = 128)
    @Comment("厂商或门店内部型号，和 SKU 不同，可重复")
    private String model;

    /** 条码预留字段，阶段一仅保存，不做扫码枪或条码打印。 */
    @Column(length = 128)
    @Comment("条码预留字段，阶段一仅保存，不做扫码枪或条码打印")
    private String barcode;

    /** 商品规格参数快照，例如镜框尺寸、镜片包装规格或护理液容量。 */
    @Column(length = 512)
    @Comment("商品规格参数快照，例如镜框尺寸、镜片包装规格或护理液容量")
    private String specification;

    /** 商品颜色，主要用于镜框、太阳镜等有颜色差异的商品。 */
    @Column(length = 64)
    @Comment("商品颜色，主要用于镜框、太阳镜等有颜色差异的商品")
    private String color;

    /** 商品材质，例如 TR90、钛、树脂等。 */
    @Column(length = 128)
    @Comment("商品材质，例如 TR90、钛、树脂等")
    private String material;

    /** 商品库存单位，决定库存数量的业务含义。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("商品库存单位：PAIR 副，PIECE 片，BOX 盒，BOTTLE 瓶，ITEM 个")
    private ProductUnit unit;

    /** 采购单价，单位为元，主要用于后续成本统计。 */
    @Column(precision = 12, scale = 2)
    @Comment("采购单价，单位为元，保留两位小数，主要用于后续成本统计")
    private BigDecimal purchasePrice;

    /** 零售标价，单位为元，账单可在此基础上人工录入实际金额。 */
    @Column(precision = 12, scale = 2)
    @Comment("零售标价，单位为元，保留两位小数，账单可在此基础上人工录入实际金额")
    private BigDecimal retailPrice;

    /** 默认供应商名称，阶段一仅作文本记录，不建立供应商档案。 */
    @Column(length = 128)
    @Comment("默认供应商名称，阶段一仅作文本记录，不建立供应商档案")
    private String supplier;

    /** 安全库存数量，库存低于该值时进入低库存提醒。 */
    @Column(nullable = false)
    @Comment("安全库存数量，整数单位，库存低于该值时进入低库存提醒")
    private Integer safeStock;

    /** 商品状态，停用或下架后不允许再新建业务单据引用。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("商品状态：ENABLED 启用，DISABLED 停用，OUT_OF_STOCK 缺货，DELISTED 下架")
    private ProductStatus status;

    /** 创建该商品档案的用户标识，用于审计追溯。 */
    @Column(length = 64)
    @Comment("创建该商品档案的用户标识，用于审计追溯")
    private String createdBy;

    /** 最后更新该商品档案的用户标识，用于审计追溯。 */
    @Column(length = 64)
    @Comment("最后更新该商品档案的用户标识，用于审计追溯")
    private String updatedBy;

    /** 商品档案创建时间，由 Hibernate 自动写入。 */
    @CreationTimestamp
    @Column(updatable = false)
    @Comment("商品档案创建时间，由 Hibernate 自动写入")
    private LocalDateTime createdAt;

    /** 商品档案最后更新时间，由 Hibernate 自动写入。 */
    @UpdateTimestamp
    @Comment("商品档案最后更新时间，由 Hibernate 自动写入")
    private LocalDateTime updatedAt;

    public enum ProductCategory {
        FRAME,
        LENS,
        SUNGLASSES,
        CONTACT_LENS,
        CARE_SOLUTION,
        ACCESSORY,
        OTHER
    }

    public enum ProductUnit {
        PAIR,
        PIECE,
        BOX,
        BOTTLE,
        ITEM
    }

    public enum ProductStatus {
        ENABLED,
        DISABLED,
        OUT_OF_STOCK,
        DELISTED
    }
}
