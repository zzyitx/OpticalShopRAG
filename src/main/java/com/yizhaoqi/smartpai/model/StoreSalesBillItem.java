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
 * 销售账单商品明细，保留账单创建时的商品快照。
 */
@Data
@Entity
@Comment("眼镜店销售账单商品明细表，保存每张账单涉及的 SKU、数量和金额快照")
@Table(name = "store_sales_bill_item")
public class StoreSalesBillItem {

    /** 账单明细自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("账单明细自增主键，仅用于系统内部关联")
    private Long id;

    /** 所属销售账单主表。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    @Comment("所属销售账单主表")
    private StoreSalesBill bill;

    /** 商品 SKU，关联商品档案业务编码。 */
    @Column(nullable = false, length = 64)
    @Comment("商品 SKU，关联商品档案业务编码")
    private String productSku;

    /** 商品名称快照，保留销售当时的可读名称。 */
    @Column(nullable = false, length = 128)
    @Comment("商品名称快照，保留销售当时的可读名称")
    private String productNameSnapshot;

    /** 销售数量，整数单位，可用于自动生成销售出库。 */
    @Column(nullable = false)
    @Comment("销售数量，整数单位，可用于自动生成销售出库")
    private Integer quantity;

    /** 销售单价，单位为元，保留两位小数。 */
    @Column(precision = 12, scale = 2)
    @Comment("销售单价，单位为元，保留两位小数")
    private BigDecimal unitPrice;

    /** 销售明细金额，单位为元，可由数量和单价计算得到。 */
    @Column(precision = 12, scale = 2)
    @Comment("销售明细金额，单位为元，可由数量和单价计算得到")
    private BigDecimal totalAmount;
}
