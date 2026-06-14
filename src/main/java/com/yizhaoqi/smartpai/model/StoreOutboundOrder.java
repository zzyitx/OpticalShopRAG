package com.yizhaoqi.smartpai.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 出库单主表，确认后扣减库存并生成库存流水。
 */
@Data
@Entity
@Comment("眼镜店出库单主表，保存销售出库、报损出库、退货出库等出库业务单据")
@Table(name = "store_outbound_order")
public class StoreOutboundOrder {

    /** 出库单自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("出库单自增主键，仅用于系统内部关联")
    private Long id;

    /** 出库单号，业务可读编号，用于库存流水追溯。 */
    @Column(nullable = false, unique = true, length = 64)
    @Comment("出库单号，业务可读编号，用于库存流水追溯")
    private String orderNo;

    /** 出库类型，用于区分销售、报损、退货、盘亏、内部领用或其他出库。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("出库类型：SALE 销售出库，LOSS 报损出库，RETURN 退货出库，DEFICIT 盘亏出库，INTERNAL 内部领用，OTHER 其他")
    private OutboundType outboundType;

    /** 出库单状态，只有 DRAFT 可以确认或取消。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("出库单状态：DRAFT 草稿，CONFIRMED 已出库，CANCELLED 已取消")
    private OutboundStatus status;

    /** 关联销售账单号，可为空，用于从账单自动出库时追溯。 */
    @Column(length = 64)
    @Comment("关联销售账单号，可为空，用于从账单自动出库时追溯")
    private String relatedBillNo;

    /** 出库单备注，记录报损原因、人工说明等。 */
    @Column(length = 512)
    @Comment("出库单备注，记录报损原因、人工说明等")
    private String remark;

    /** 确认出库操作人，用于审计追溯。 */
    @Column(length = 64)
    @Comment("确认出库操作人，用于审计追溯")
    private String confirmedBy;

    /** 确认出库时间，库存扣减和流水写入以该时间为准。 */
    @Comment("确认出库时间，库存扣减和流水写入以该时间为准")
    private LocalDateTime confirmedAt;

    /** 取消草稿出库单的操作人，用于审计追溯。 */
    @Column(length = 64)
    @Comment("取消草稿出库单的操作人，用于审计追溯")
    private String cancelledBy;

    /** 取消草稿出库单的时间；已确认单据不能取消。 */
    @Comment("取消草稿出库单的时间；已确认单据不能取消")
    private LocalDateTime cancelledAt;

    /** 创建出库单的用户标识。 */
    @Column(length = 64)
    @Comment("创建出库单的用户标识")
    private String createdBy;

    /** 出库单创建时间，由 Hibernate 自动写入。 */
    @CreationTimestamp
    @Column(updatable = false)
    @Comment("出库单创建时间，由 Hibernate 自动写入")
    private LocalDateTime createdAt;

    /** 出库单最后更新时间，由 Hibernate 自动写入。 */
    @UpdateTimestamp
    @Comment("出库单最后更新时间，由 Hibernate 自动写入")
    private LocalDateTime updatedAt;

    /** 出库商品明细，确认时逐行扣减库存。 */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Comment("出库商品明细，确认时逐行扣减库存")
    private List<StoreOutboundItem> items = new ArrayList<>();

    public enum OutboundType {
        SALE,
        LOSS,
        RETURN,
        DEFICIT,
        INTERNAL,
        OTHER
    }

    public enum OutboundStatus {
        DRAFT,
        CONFIRMED,
        CANCELLED
    }
}
