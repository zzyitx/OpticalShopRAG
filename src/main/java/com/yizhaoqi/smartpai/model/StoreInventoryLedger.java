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
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 库存流水，记录每一次库存变化前后的数量。
 */
@Data
@Entity
@Comment("眼镜店库存流水表，保存每次入库、出库或调整造成的库存变化前后数量")
@Table(
        name = "store_inventory_ledger",
        indexes = {
                @Index(name = "idx_store_inventory_ledger_sku", columnList = "product_sku"),
                @Index(name = "idx_store_inventory_ledger_order_no", columnList = "business_order_no")
        }
)
public class StoreInventoryLedger {

    /** 库存流水自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("库存流水自增主键，仅用于系统内部关联")
    private Long id;

    /** 发生库存变化的商品 SKU。 */
    @Column(name = "product_sku", nullable = false, length = 64)
    @Comment("发生库存变化的商品 SKU")
    private String productSku;

    /** 仓库编码，阶段一固定为 DEFAULT。 */
    @Column(nullable = false, length = 64)
    @Comment("仓库编码，阶段一固定为 DEFAULT")
    private String warehouseCode;

    /** 库存变化类型，入库为正向变化，出库为负向变化。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("库存变化类型：INBOUND 入库，OUTBOUND 出库，ADJUST 调整，REVERSE 红冲，CANCEL 取消")
    private ChangeType changeType;

    /** 触发库存变化的业务单号，例如入库单号、出库单号或账单号。 */
    @Column(name = "business_order_no", nullable = false, length = 64)
    @Comment("触发库存变化的业务单号，例如入库单号、出库单号或账单号")
    private String businessOrderNo;

    /** 变化前库存数量，整数单位。 */
    @Column(nullable = false)
    @Comment("变化前库存数量，整数单位")
    private Integer quantityBefore;

    /** 本次变化数量，入库为正数，出库为负数。 */
    @Column(nullable = false)
    @Comment("本次变化数量，入库为正数，出库为负数")
    private Integer changeQuantity;

    /** 变化后库存数量，整数单位。 */
    @Column(nullable = false)
    @Comment("变化后库存数量，整数单位")
    private Integer quantityAfter;

    /** 操作人用户标识，用于审计追溯。 */
    @Column(length = 64)
    @Comment("操作人用户标识，用于审计追溯")
    private String operator;

    /** 操作发生时间，库存变化以该时间排序。 */
    @Column(nullable = false)
    @Comment("操作发生时间，库存变化以该时间排序")
    private LocalDateTime operatedAt;

    /** 操作来源，用于区分入库单、出库单、账单、导入或系统任务。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Comment("操作来源：INBOUND_ORDER 入库单，OUTBOUND_ORDER 出库单，SALES_BILL 账单，IMPORT 导入，SYSTEM 系统")
    private OperationSource operationSource;

    /** 库存流水备注，记录额外业务说明。 */
    @Column(length = 512)
    @Comment("库存流水备注，记录额外业务说明")
    private String remark;

    public enum ChangeType {
        INBOUND,
        OUTBOUND,
        ADJUST,
        REVERSE,
        CANCEL
    }

    public enum OperationSource {
        INBOUND_ORDER,
        OUTBOUND_ORDER,
        SALES_BILL,
        IMPORT,
        SYSTEM
    }
}
