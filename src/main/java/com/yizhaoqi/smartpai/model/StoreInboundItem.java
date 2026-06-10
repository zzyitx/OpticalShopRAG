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
import java.time.LocalDate;

/**
 * 入库单商品明细，记录每个 SKU 的入库数量和金额快照。
 */
@Data
@Entity
@Comment("眼镜店入库单明细表，保存每个入库 SKU 的数量、单价和批次信息")
@Table(name = "store_inbound_item")
public class StoreInboundItem {

    /** 入库明细自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("入库明细自增主键，仅用于系统内部关联")
    private Long id;

    /** 所属入库单主表。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Comment("所属入库单主表")
    private StoreInboundOrder order;

    /** 入库商品 SKU，引用商品档案业务编码。 */
    @Column(nullable = false, length = 64)
    @Comment("入库商品 SKU，引用商品档案业务编码")
    private String productSku;

    /** 商品名称快照，保留入库当时的可读名称。 */
    @Column(nullable = false, length = 128)
    @Comment("商品名称快照，保留入库当时的可读名称")
    private String productNameSnapshot;

    /** 入库数量，整数单位，确认后增加库存。 */
    @Column(nullable = false)
    @Comment("入库数量，整数单位，确认后增加库存")
    private Integer quantity;

    /** 入库单价，单位为元，保留两位小数。 */
    @Column(precision = 12, scale = 2)
    @Comment("入库单价，单位为元，保留两位小数")
    private BigDecimal unitPrice;

    /** 入库总金额，单位为元，可由数量和单价计算得到。 */
    @Column(precision = 12, scale = 2)
    @Comment("入库总金额，单位为元，可由数量和单价计算得到")
    private BigDecimal totalAmount;

    /** 商品批次号，适用于隐形眼镜、护理液等批次管理商品。 */
    @Column(length = 128)
    @Comment("商品批次号，适用于隐形眼镜、护理液等批次管理商品")
    private String batchNo;

    /** 生产日期，适用于有生产日期的商品，可为空。 */
    @Comment("生产日期，适用于有生产日期的商品，可为空")
    private LocalDate productionDate;

    /** 有效期，适用于护理液、隐形眼镜等商品，可为空。 */
    @Comment("有效期，适用于护理液、隐形眼镜等商品，可为空")
    private LocalDate expirationDate;
}
