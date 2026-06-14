package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreSalesBill;
import com.yizhaoqi.smartpai.model.StoreSalesBillChangeLog;
import com.yizhaoqi.smartpai.model.StoreSalesBillItem;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.repository.StoreSalesBillChangeLogRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理销售账单、客户配镜历史、度数展示值和账单修改审计记录。
 */
@Service
public class StoreSalesBillService {

    private static final DateTimeFormatter BILL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StoreSalesBillRepository salesBillRepository;
    private final StoreSalesBillChangeLogRepository changeLogRepository;
    private final StoreInventoryService inventoryService;
    private final StoreProductRepository productRepository;

    public StoreSalesBillService(StoreSalesBillRepository salesBillRepository,
                                 StoreSalesBillChangeLogRepository changeLogRepository,
                                 StoreInventoryService inventoryService,
                                 StoreProductRepository productRepository) {
        this.salesBillRepository = salesBillRepository;
        this.changeLogRepository = changeLogRepository;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
    }

    @Transactional
    public SalesBillView createBill(SalesBillCreateRequest request, String operator) {
        validateBaseRequest(request.customerName(), request.customerPhone(), request.purchaseDate());

        StoreSalesBill bill = new StoreSalesBill();
        bill.setBillNo(generateBillNo());
        applyFields(bill, request, operator);
        applyItems(bill, request.items());
        bill.setCreatedBy(operator);

        // 手机号只用于历史查询，不做唯一约束；同一客户每次配镜必须新增一条账单记录。
        StoreSalesBill saved = salesBillRepository.save(bill);
        if (request.autoOutbound() && !saved.getItems().isEmpty()) {
            // 自动销售出库与账单创建共享事务；库存不足时整张账单和出库单一起回滚。
            StoreInventoryService.OutboundOrderView outbound = inventoryService.createOutbound(
                    new StoreInventoryService.OutboundOrderCreateRequest(
                            StoreOutboundOrder.OutboundType.SALE,
                            saved.getBillNo(),
                            "销售账单自动出库",
                            saved.getItems().stream()
                                    .map(item -> new StoreInventoryService.OutboundItemRequest(
                                            item.getProductSku(),
                                            item.getProductNameSnapshot(),
                                            item.getQuantity(),
                                            item.getUnitPrice()
                                    ))
                                    .toList()
                    ),
                    operator
            );
            inventoryService.confirmOutbound(outbound.id(), operator);
        }
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<SalesBillView> listBills() {
        // 列表以购买日期倒序为主，创建时间倒序兜底，便于门店按最近成交快速核对账单。
        return salesBillRepository.findAllByOrderByPurchaseDateDescCreatedAtDesc()
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalesBillView> getCustomerHistory(String customerPhone) {
        if (!StringUtils.hasText(customerPhone)) {
            throw new CustomException("STORE_SALES_BILL_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        return salesBillRepository.findByCustomerPhoneOrderByPurchaseDateDescCreatedAtDesc(customerPhone.trim())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeLogView> getChanges(Long billId) {
        return changeLogRepository.findByBillIdOrderByChangedAtDesc(billId).stream()
                .map(log -> new ChangeLogView(
                        log.getId(),
                        log.getBillId(),
                        log.getBillNo(),
                        log.getBeforeSnapshot(),
                        log.getAfterSnapshot(),
                        log.getChangedBy(),
                        log.getChangedAt()
                ))
                .toList();
    }

    @Transactional
    public SalesBillView updateBill(Long id, SalesBillUpdateRequest request, String operator) {
        StoreSalesBill bill = salesBillRepository.findById(id)
                .orElseThrow(() -> new CustomException("STORE_SALES_BILL_NOT_FOUND", HttpStatus.NOT_FOUND));
        validateBaseRequest(request.customerName(), request.customerPhone(), request.purchaseDate());

        String before = snapshotBill(bill);
        applyFields(bill, request.toCreateRequest(), operator);
        StoreSalesBill saved = salesBillRepository.save(bill);

        // 账单修改不允许静默覆盖，保存后写入关键字段前后摘要，便于追踪金额和度数变化。
        StoreSalesBillChangeLog changeLog = new StoreSalesBillChangeLog();
        changeLog.setBillId(saved.getId());
        changeLog.setBillNo(saved.getBillNo());
        changeLog.setBeforeSnapshot(before);
        changeLog.setAfterSnapshot(snapshotBill(saved));
        changeLog.setChangedBy(operator);
        changeLogRepository.save(changeLog);

        return toView(saved);
    }

    private void validateBaseRequest(String customerName, String customerPhone, LocalDate purchaseDate) {
        if (!StringUtils.hasText(customerName)) {
            throw new CustomException("STORE_SALES_BILL_CUSTOMER_NAME_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(customerPhone)) {
            throw new CustomException("STORE_SALES_BILL_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (purchaseDate == null) {
            throw new CustomException("STORE_SALES_BILL_PURCHASE_DATE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
    }

    private void applyFields(StoreSalesBill bill, SalesBillCreateRequest request, String operator) {
        // 左右眼度数和金额按权威字段分别保存，组合展示字符串在响应阶段统一派生。
        bill.setCustomerName(request.customerName().trim());
        bill.setCustomerPhone(request.customerPhone().trim());
        bill.setPurchaseDate(request.purchaseDate());
        bill.setLeftMyopiaDegree(request.leftMyopiaDegree());
        bill.setLeftAstigmatism(request.leftAstigmatism());
        bill.setLeftAxis(request.leftAxis());
        bill.setRightMyopiaDegree(request.rightMyopiaDegree());
        bill.setRightAstigmatism(request.rightAstigmatism());
        bill.setRightAxis(request.rightAxis());
        bill.setPupillaryDistance(request.pupillaryDistance());
        bill.setFrameModel(trimToNull(request.frameModel()));
        bill.setLensModel(trimToNull(request.lensModel()));
        bill.setPaymentAmount(defaultMoney(request.paymentAmount()));
        bill.setDiscountAmount(defaultMoney(request.discountAmount()));
        bill.setActualAmount(defaultMoney(request.actualAmount()));
        bill.setPaymentMethod(request.paymentMethod());
        bill.setSalesperson(trimToNull(request.salesperson()));
        bill.setOptometrist(trimToNull(request.optometrist()));
        bill.setRemark(trimToNull(request.remark()));
        bill.setUpdatedBy(operator);
    }

    private void applyItems(StoreSalesBill bill, List<SalesBillItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            return;
        }
        for (SalesBillItemRequest request : itemRequests) {
            if (!StringUtils.hasText(request.productSku()) || request.quantity() == null || request.quantity() <= 0) {
                throw new CustomException("STORE_SALES_BILL_ITEM_INVALID", HttpStatus.BAD_REQUEST);
            }
            StoreProduct product = productRepository.findBySku(request.productSku().trim())
                    .orElseThrow(() -> new CustomException("STORE_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
            if (product.getStatus() == StoreProduct.ProductStatus.DISABLED
                    || product.getStatus() == StoreProduct.ProductStatus.DELISTED) {
                throw new CustomException("STORE_PRODUCT_NOT_USABLE", HttpStatus.CONFLICT);
            }
            StoreSalesBillItem item = new StoreSalesBillItem();
            item.setBill(bill);
            item.setProductSku(product.getSku());
            item.setProductNameSnapshot(product.getName());
            item.setQuantity(request.quantity());
            item.setUnitPrice(defaultMoney(request.unitPrice()));
            item.setTotalAmount(defaultMoney(request.unitPrice()).multiply(BigDecimal.valueOf(request.quantity())));
            bill.getItems().add(item);
        }
    }

    private SalesBillView toView(StoreSalesBill bill) {
        return new SalesBillView(
                bill.getId(),
                bill.getBillNo(),
                bill.getCustomerName(),
                bill.getCustomerPhone(),
                bill.getPurchaseDate(),
                bill.getLeftMyopiaDegree(),
                bill.getLeftAstigmatism(),
                bill.getLeftAxis(),
                formatEyeDegree(bill.getLeftMyopiaDegree(), bill.getLeftAstigmatism(), bill.getLeftAxis()),
                bill.getRightMyopiaDegree(),
                bill.getRightAstigmatism(),
                bill.getRightAxis(),
                formatEyeDegree(bill.getRightMyopiaDegree(), bill.getRightAstigmatism(), bill.getRightAxis()),
                bill.getPupillaryDistance(),
                bill.getFrameModel(),
                bill.getLensModel(),
                bill.getPaymentAmount(),
                bill.getDiscountAmount(),
                bill.getActualAmount(),
                bill.getPaymentMethod(),
                bill.getSalesperson(),
                bill.getOptometrist(),
                bill.getRemark(),
                bill.getCreatedAt(),
                bill.getUpdatedAt(),
                bill.getItems().stream().map(this::toItemView).toList()
        );
    }

    private SalesBillItemView toItemView(StoreSalesBillItem item) {
        return new SalesBillItemView(
                item.getId(),
                item.getProductSku(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalAmount()
        );
    }

    private String snapshotBill(StoreSalesBill bill) {
        // 快照只保留排查账单修改时最关键的客户、日期、度数和金额字段。
        return "customerPhone=" + bill.getCustomerPhone()
                + ";purchaseDate=" + bill.getPurchaseDate()
                + ";left=" + formatEyeDegree(bill.getLeftMyopiaDegree(), bill.getLeftAstigmatism(), bill.getLeftAxis())
                + ";right=" + formatEyeDegree(bill.getRightMyopiaDegree(), bill.getRightAstigmatism(), bill.getRightAxis())
                + ";paymentAmount=" + bill.getPaymentAmount()
                + ";actualAmount=" + bill.getActualAmount();
    }

    private String formatEyeDegree(BigDecimal myopia, BigDecimal astigmatism, Integer axis) {
        return formatDecimal(myopia) + "-" + formatDecimal(astigmatism) + "-" + (axis == null ? "" : axis);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String generateBillNo() {
        return "SB-" + LocalDate.now().format(BILL_DATE_FORMATTER) + "-" + System.nanoTime();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record SalesBillCreateRequest(
            String customerName,
            String customerPhone,
            LocalDate purchaseDate,
            BigDecimal leftMyopiaDegree,
            BigDecimal leftAstigmatism,
            Integer leftAxis,
            BigDecimal rightMyopiaDegree,
            BigDecimal rightAstigmatism,
            Integer rightAxis,
            BigDecimal pupillaryDistance,
            String frameModel,
            String lensModel,
            BigDecimal paymentAmount,
            BigDecimal discountAmount,
            BigDecimal actualAmount,
            StoreSalesBill.PaymentMethod paymentMethod,
            String salesperson,
            String optometrist,
            String remark,
            List<SalesBillItemRequest> items,
            boolean autoOutbound
    ) {
    }

    public record SalesBillUpdateRequest(
            String customerName,
            String customerPhone,
            LocalDate purchaseDate,
            BigDecimal leftMyopiaDegree,
            BigDecimal leftAstigmatism,
            Integer leftAxis,
            BigDecimal rightMyopiaDegree,
            BigDecimal rightAstigmatism,
            Integer rightAxis,
            BigDecimal pupillaryDistance,
            String frameModel,
            String lensModel,
            BigDecimal paymentAmount,
            BigDecimal discountAmount,
            BigDecimal actualAmount,
            StoreSalesBill.PaymentMethod paymentMethod,
            String salesperson,
            String optometrist,
            String remark
    ) {
        private SalesBillCreateRequest toCreateRequest() {
            return new SalesBillCreateRequest(
                    customerName,
                    customerPhone,
                    purchaseDate,
                    leftMyopiaDegree,
                    leftAstigmatism,
                    leftAxis,
                    rightMyopiaDegree,
                    rightAstigmatism,
                    rightAxis,
                    pupillaryDistance,
                    frameModel,
                    lensModel,
                    paymentAmount,
                    discountAmount,
                    actualAmount,
                    paymentMethod,
                    salesperson,
                    optometrist,
                    remark,
                    List.of(),
                    false
            );
        }
    }

    public record SalesBillView(
            Long id,
            String billNo,
            String customerName,
            String customerPhone,
            LocalDate purchaseDate,
            BigDecimal leftMyopiaDegree,
            BigDecimal leftAstigmatism,
            Integer leftAxis,
            String leftDegreeDisplay,
            BigDecimal rightMyopiaDegree,
            BigDecimal rightAstigmatism,
            Integer rightAxis,
            String rightDegreeDisplay,
            BigDecimal pupillaryDistance,
            String frameModel,
            String lensModel,
            BigDecimal paymentAmount,
            BigDecimal discountAmount,
            BigDecimal actualAmount,
            StoreSalesBill.PaymentMethod paymentMethod,
            String salesperson,
            String optometrist,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<SalesBillItemView> items
    ) {
    }

    public record SalesBillItemRequest(
            String productSku,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal unitPrice
    ) {
    }

    public record SalesBillItemView(
            Long id,
            String productSku,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount
    ) {
    }

    public record ChangeLogView(
            Long id,
            Long billId,
            String billNo,
            String beforeSnapshot,
            String afterSnapshot,
            String changedBy,
            LocalDateTime changedAt
    ) {
    }
}
