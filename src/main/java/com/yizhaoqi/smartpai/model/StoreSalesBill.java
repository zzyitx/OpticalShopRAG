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
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售账单和客户配镜记录，每次配镜生成一条新记录。
 */
@Data
@Entity
@Comment("眼镜店销售账单表，同时作为客户每次配镜历史记录的权威数据来源")
@Table(
        name = "store_sales_bill",
        indexes = {
                @Index(name = "idx_store_sales_bill_no", columnList = "bill_no"),
                @Index(name = "idx_store_sales_bill_phone", columnList = "customer_phone"),
                @Index(name = "idx_store_sales_bill_purchase_date", columnList = "purchase_date")
        }
)
public class StoreSalesBill {

    /** 销售账单自增主键，仅用于系统内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("销售账单自增主键，仅用于系统内部关联")
    private Long id;

    /** 账单号，业务可读编号，用于查询、出库关联和审计追溯。 */
    @Column(name = "bill_no", nullable = false, unique = true, length = 64)
    @Comment("账单号，业务可读编号，用于查询、出库关联和审计追溯")
    private String billNo;

    /** 客户姓名，随账单保存，不建立独立客户档案。 */
    @Column(nullable = false, length = 64)
    @Comment("客户姓名，随账单保存，不建立独立客户档案")
    private String customerName;

    /** 客户手机号，必填，用于区分同名客户和查询历史配镜记录。 */
    @Column(name = "customer_phone", nullable = false, length = 32)
    @Comment("客户手机号，必填，用于区分同名客户和查询历史配镜记录")
    private String customerPhone;

    /** 购买日期，按门店实际成交日期记录。 */
    @Column(name = "purchase_date", nullable = false)
    @Comment("购买日期，按门店实际成交日期记录")
    private LocalDate purchaseDate;

    /** 左眼近视度数，数据库拆分存储，页面和 AI 响应再组合展示。 */
    @Column(precision = 6, scale = 2)
    @Comment("左眼近视度数，数据库拆分存储，页面和 AI 响应再组合展示")
    private BigDecimal leftMyopiaDegree;

    /** 左眼散光度数，数据库拆分存储，页面和 AI 响应再组合展示。 */
    @Column(precision = 6, scale = 2)
    @Comment("左眼散光度数，数据库拆分存储，页面和 AI 响应再组合展示")
    private BigDecimal leftAstigmatism;

    /** 左眼轴位，单位为度，通常范围 0 到 180。 */
    @Comment("左眼轴位，单位为度，通常范围 0 到 180")
    private Integer leftAxis;

    /** 右眼近视度数，数据库拆分存储，页面和 AI 响应再组合展示。 */
    @Column(precision = 6, scale = 2)
    @Comment("右眼近视度数，数据库拆分存储，页面和 AI 响应再组合展示")
    private BigDecimal rightMyopiaDegree;

    /** 右眼散光度数，数据库拆分存储，页面和 AI 响应再组合展示。 */
    @Column(precision = 6, scale = 2)
    @Comment("右眼散光度数，数据库拆分存储，页面和 AI 响应再组合展示")
    private BigDecimal rightAstigmatism;

    /** 右眼轴位，单位为度，通常范围 0 到 180。 */
    @Comment("右眼轴位，单位为度，通常范围 0 到 180")
    private Integer rightAxis;

    /** 瞳距，单位为毫米，可为空。 */
    @Column(precision = 6, scale = 2)
    @Comment("瞳距，单位为毫米，可为空")
    private BigDecimal pupillaryDistance;

    /** 镜框型号快照，记录本次配镜选择的镜框。 */
    @Column(length = 128)
    @Comment("镜框型号快照，记录本次配镜选择的镜框")
    private String frameModel;

    /** 镜片型号快照，记录本次配镜选择的镜片。 */
    @Column(length = 128)
    @Comment("镜片型号快照，记录本次配镜选择的镜片")
    private String lensModel;

    /** 应付金额，单位为元，人工录入，阶段一不对接真实支付。 */
    @Column(precision = 12, scale = 2)
    @Comment("应付金额，单位为元，人工录入，阶段一不对接真实支付")
    private BigDecimal paymentAmount;

    /** 优惠金额，单位为元，人工录入。 */
    @Column(precision = 12, scale = 2)
    @Comment("优惠金额，单位为元，人工录入")
    private BigDecimal discountAmount;

    /** 实收金额，单位为元，人工录入。 */
    @Column(precision = 12, scale = 2)
    @Comment("实收金额，单位为元，人工录入")
    private BigDecimal actualAmount;

    /** 支付方式，仅记录文本枚举，不对接真实支付。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    @Comment("支付方式：CASH 现金，WECHAT 微信，ALIPAY 支付宝，BANK_CARD 银行卡，OTHER 其他")
    private PaymentMethod paymentMethod;

    /** 销售员姓名或账号快照。 */
    @Column(length = 64)
    @Comment("销售员姓名或账号快照")
    private String salesperson;

    /** 验光师姓名或账号快照，可为空。 */
    @Column(length = 64)
    @Comment("验光师姓名或账号快照，可为空")
    private String optometrist;

    /** 账单备注，记录复配、售后或人工说明。 */
    @Column(length = 512)
    @Comment("账单备注，记录复配、售后或人工说明")
    private String remark;

    /** 创建账单的用户标识，用于审计追溯。 */
    @Column(length = 64)
    @Comment("创建账单的用户标识，用于审计追溯")
    private String createdBy;

    /** 最后修改账单的用户标识，用于审计追溯。 */
    @Column(length = 64)
    @Comment("最后修改账单的用户标识，用于审计追溯")
    private String updatedBy;

    /** 账单创建时间，由 Hibernate 自动写入。 */
    @CreationTimestamp
    @Column(updatable = false)
    @Comment("账单创建时间，由 Hibernate 自动写入")
    private LocalDateTime createdAt;

    /** 账单最后更新时间，由 Hibernate 自动写入。 */
    @UpdateTimestamp
    @Comment("账单最后更新时间，由 Hibernate 自动写入")
    private LocalDateTime updatedAt;

    /** 账单商品明细，记录本次销售涉及的商品 SKU 和数量。 */
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Comment("账单商品明细，记录本次销售涉及的商品 SKU 和数量")
    private List<StoreSalesBillItem> items = new ArrayList<>();

    public enum PaymentMethod {
        CASH,
        WECHAT,
        ALIPAY,
        BANK_CARD,
        OTHER
    }
}
