package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

/**
 * 出库单商品明细，记录每个 SKU 的出库数量和金额快照。
 */
@Data
@Entity
@Comment("眼镜店出库单明细表，保存每个出库 SKU 的数量、单价和关联单据快照")
@Table(name = "store_outbound_item")
public class StoreOutboundItem {

    /** 出库明细自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("出库明细自增主键，仅用于系统内部关联")
    private Long id;

    /** 所属出库单主表。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Comment("所属出库单主表")
    private StoreOutboundOrder order;

    /** 出库商品 SKU，引用商品档案业务编码。 */
    @Column(nullable = false, length = 64)
    @Comment("出库商品 SKU，引用商品档案业务编码")
    private String productSku;

    /** 商品名称快照，保留出库当时的可读名称。 */
    @Column(nullable = false, length = 128)
    @Comment("商品名称快照，保留出库当时的可读名称")
    private String productNameSnapshot;

    /** 出库数量，整数单位，确认后扣减库存。 */
    @Column(nullable = false)
    @Comment("出库数量，整数单位，确认后扣减库存")
    private Integer quantity;

    /** 出库单价，单位为元，保留两位小数。 */
    @Column(precision = 12, scale = 2)
    @Comment("出库单价，单位为元，保留两位小数")
    private BigDecimal unitPrice;

    /** 出库总金额，单位为元，可由数量和单价计算得到。 */
    @Column(precision = 12, scale = 2)
    @Comment("出库总金额，单位为元，可由数量和单价计算得到")
    private BigDecimal totalAmount;
}
