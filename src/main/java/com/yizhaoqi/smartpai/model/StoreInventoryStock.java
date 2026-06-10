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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 单店单仓库存现存量，作为库存查询和出库校验的权威数量来源。
 */
@Data
@Entity
@Comment("眼镜店库存现存量表，保存单仓下每个商品 SKU 的当前库存和安全库存信息")
@Table(
        name = "store_inventory_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_store_inventory_stock_sku_warehouse", columnNames = {"product_sku", "warehouse_code"}),
        indexes = {
                @Index(name = "idx_store_inventory_stock_sku", columnList = "product_sku"),
                @Index(name = "idx_store_inventory_stock_status", columnList = "status")
        }
)
public class StoreInventoryStock {

    /** 库存记录自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("库存记录自增主键，仅用于系统内部关联")
    private Long id;

    /** 商品 SKU，关联商品档案的业务唯一编码。 */
    @Column(name = "product_sku", nullable = false, length = 64)
    @Comment("商品 SKU，关联商品档案的业务唯一编码")
    private String productSku;

    /** 仓库编码，阶段一固定为 DEFAULT，预留多仓扩展。 */
    @Column(name = "warehouse_code", nullable = false, length = 64)
    @Comment("仓库编码，阶段一固定为 DEFAULT，预留多仓扩展")
    private String warehouseCode;

    /** 当前库存数量，整数单位，是出库校验的权威数量。 */
    @Column(nullable = false)
    @Comment("当前库存数量，整数单位，是出库校验的权威数量")
    private Integer currentQuantity;

    /** 可用库存数量，阶段一默认等于当前库存，预留锁库存场景。 */
    @Column(nullable = false)
    @Comment("可用库存数量，阶段一默认等于当前库存，预留锁库存场景")
    private Integer availableQuantity;

    /** 安全库存数量，低于该值时进入低库存提醒。 */
    @Column(nullable = false)
    @Comment("安全库存数量，整数单位，低于该值时进入低库存提醒")
    private Integer safeStock;

    /** 最近一次确认入库时间，用于库存列表排序和追溯。 */
    @Comment("最近一次确认入库时间，用于库存列表排序和追溯")
    private LocalDateTime lastInboundAt;

    /** 最近一次确认出库时间，用于库存列表排序和追溯。 */
    @Comment("最近一次确认出库时间，用于库存列表排序和追溯")
    private LocalDateTime lastOutboundAt;

    /** 库存状态，按当前库存与安全库存推导并保存便于查询。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("库存状态：NORMAL 正常，LOW_STOCK 低库存，OUT_OF_STOCK 缺货，DISABLED 停用")
    private StockStatus status;

    /** 库存记录最后更新时间，由 Hibernate 自动写入。 */
    @UpdateTimestamp
    @Comment("库存记录最后更新时间，由 Hibernate 自动写入")
    private LocalDateTime updatedAt;

    public enum StockStatus {
        NORMAL,
        LOW_STOCK,
        OUT_OF_STOCK,
        DISABLED
    }
}
