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
 * 入库单主表，确认后增加库存并生成库存流水。
 */
@Data
@Entity
@Comment("眼镜店入库单主表，保存采购入库、退货入库、初始化入库等入库业务单据")
@Table(name = "store_inbound_order")
public class StoreInboundOrder {

    /** 入库单自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("入库单自增主键，仅用于系统内部关联")
    private Long id;

    /** 入库单号，业务可读编号，用于库存流水追溯。 */
    @Column(nullable = false, unique = true, length = 64)
    @Comment("入库单号，业务可读编号，用于库存流水追溯")
    private String orderNo;

    /** 入库类型，用于区分采购、退货、盘盈、初始化或其他入库。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("入库类型：PURCHASE 采购入库，RETURN 退货入库，PROFIT 盘盈入库，INITIAL 初始入库，OTHER 其他")
    private InboundType inboundType;

    /** 入库单状态，只有 DRAFT 可以确认或取消。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("入库单状态：DRAFT 草稿，CONFIRMED 已入库，CANCELLED 已取消")
    private InboundStatus status;

    /** 供应商文本快照，阶段一不建立供应商档案。 */
    @Column(length = 128)
    @Comment("供应商文本快照，阶段一不建立供应商档案")
    private String supplier;

    /** 入库单备注，记录采购单、批次或人工说明。 */
    @Column(length = 512)
    @Comment("入库单备注，记录采购单、批次或人工说明")
    private String remark;

    /** 确认入库操作人，用于审计追溯。 */
    @Column(length = 64)
    @Comment("确认入库操作人，用于审计追溯")
    private String confirmedBy;

    /** 确认入库时间，库存增加和流水写入以该时间为准。 */
    @Comment("确认入库时间，库存增加和流水写入以该时间为准")
    private LocalDateTime confirmedAt;

    /** 取消草稿入库单的操作人，用于审计追溯。 */
    @Column(length = 64)
    @Comment("取消草稿入库单的操作人，用于审计追溯")
    private String cancelledBy;

    /** 取消草稿入库单的时间；已确认单据不能取消。 */
    @Comment("取消草稿入库单的时间；已确认单据不能取消")
    private LocalDateTime cancelledAt;

    /** 创建入库单的用户标识。 */
    @Column(length = 64)
    @Comment("创建入库单的用户标识")
    private String createdBy;

    /** 入库单创建时间，由 Hibernate 自动写入。 */
    @CreationTimestamp
    @Column(updatable = false)
    @Comment("入库单创建时间，由 Hibernate 自动写入")
    private LocalDateTime createdAt;

    /** 入库单最后更新时间，由 Hibernate 自动写入。 */
    @UpdateTimestamp
    @Comment("入库单最后更新时间，由 Hibernate 自动写入")
    private LocalDateTime updatedAt;

    /** 入库商品明细，确认时逐行增加库存。 */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Comment("入库商品明细，确认时逐行增加库存")
    private List<StoreInboundItem> items = new ArrayList<>();

    public enum InboundType {
        PURCHASE,
        RETURN,
        PROFIT,
        INITIAL,
        OTHER
    }

    public enum InboundStatus {
        DRAFT,
        CONFIRMED,
        CANCELLED
    }
}
