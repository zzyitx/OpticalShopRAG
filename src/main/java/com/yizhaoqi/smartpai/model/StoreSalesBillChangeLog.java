package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 销售账单修改记录，防止关键字段被静默覆盖。
 */
@Data
@Entity
@Comment("眼镜店销售账单修改记录表，保存每次账单关键字段变更的前后快照摘要")
@Table(name = "store_sales_bill_change_log")
public class StoreSalesBillChangeLog {

    /** 修改记录自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("修改记录自增主键，仅用于系统内部关联")
    private Long id;

    /** 被修改账单的主键 ID。 */
    @Column(nullable = false)
    @Comment("被修改账单的主键 ID")
    private Long billId;

    /** 被修改账单的业务账单号。 */
    @Column(nullable = false, length = 64)
    @Comment("被修改账单的业务账单号")
    private String billNo;

    /** 修改前摘要，记录关键字段的旧值。 */
    @Column(nullable = false, length = 1024)
    @Comment("修改前摘要，记录关键字段的旧值")
    private String beforeSnapshot;

    /** 修改后摘要，记录关键字段的新值。 */
    @Column(nullable = false, length = 1024)
    @Comment("修改后摘要，记录关键字段的新值")
    private String afterSnapshot;

    /** 修改操作人，用于审计追溯。 */
    @Column(length = 64)
    @Comment("修改操作人，用于审计追溯")
    private String changedBy;

    /** 修改时间，由 Hibernate 自动写入。 */
    @CreationTimestamp
    @Comment("修改时间，由 Hibernate 自动写入")
    private LocalDateTime changedAt;
}
