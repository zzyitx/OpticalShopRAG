package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreInboundItem;
import com.yizhaoqi.smartpai.model.StoreInboundOrder;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreOutboundItem;
import com.yizhaoqi.smartpai.model.StoreOutboundOrder;
import com.yizhaoqi.smartpai.repository.StoreInboundOrderRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryLedgerRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreOutboundOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理入库单、出库单、权威库存数量和不可变库存流水。
 */
@Service
public class StoreInventoryService {

    public static final String DEFAULT_WAREHOUSE_CODE = "DEFAULT";
    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StoreInventoryStockRepository stockRepository;
    private final StoreInventoryLedgerRepository ledgerRepository;
    private final StoreInboundOrderRepository inboundOrderRepository;
    private final StoreOutboundOrderRepository outboundOrderRepository;

    public StoreInventoryService(StoreInventoryStockRepository stockRepository,
                                 StoreInventoryLedgerRepository ledgerRepository,
                                 StoreInboundOrderRepository inboundOrderRepository,
                                 StoreOutboundOrderRepository outboundOrderRepository) {
        this.stockRepository = stockRepository;
        this.ledgerRepository = ledgerRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.outboundOrderRepository = outboundOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<StockView> listStocks() {
        return stockRepository.findAll().stream()
                .map(this::toStockView)
                .toList();
    }

    @Transactional
    public InboundOrderView createInbound(InboundOrderCreateRequest request, String operator) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new CustomException("STORE_INBOUND_ITEMS_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        StoreInboundOrder order = new StoreInboundOrder();
        order.setOrderNo(generateOrderNo("IN"));
        order.setInboundType(request.inboundType() == null ? StoreInboundOrder.InboundType.OTHER : request.inboundType());
        order.setStatus(StoreInboundOrder.InboundStatus.DRAFT);
        order.setSupplier(request.supplier());
        order.setRemark(request.remark());
        order.setCreatedBy(operator);

        // 草稿只记录单据和明细，不触碰库存；库存变化必须等确认入库时在同一事务内完成。
        for (InboundItemRequest itemRequest : request.items()) {
            validatePositiveQuantity(itemRequest.quantity());
            StoreInboundItem item = new StoreInboundItem();
            item.setOrder(order);
            item.setProductSku(itemRequest.productSku());
            item.setProductNameSnapshot(itemRequest.productNameSnapshot());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(defaultMoney(itemRequest.unitPrice()));
            item.setTotalAmount(defaultMoney(itemRequest.unitPrice()).multiply(BigDecimal.valueOf(itemRequest.quantity())));
            order.getItems().add(item);
        }

        StoreInboundOrder saved = inboundOrderRepository.save(order);
        return new InboundOrderView(saved.getId(), saved.getOrderNo(), saved.getStatus());
    }

    @Transactional
    public OutboundOrderView createOutbound(OutboundOrderCreateRequest request, String operator) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new CustomException("STORE_OUTBOUND_ITEMS_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        StoreOutboundOrder order = new StoreOutboundOrder();
        order.setOrderNo(generateOrderNo("OUT"));
        order.setOutboundType(request.outboundType() == null ? StoreOutboundOrder.OutboundType.OTHER : request.outboundType());
        order.setStatus(StoreOutboundOrder.OutboundStatus.DRAFT);
        order.setRelatedBillNo(request.relatedBillNo());
        order.setRemark(request.remark());
        order.setCreatedBy(operator);

        // 草稿出库不预占库存；阶段一库存不足校验统一放在确认出库事务中执行。
        for (OutboundItemRequest itemRequest : request.items()) {
            validatePositiveQuantity(itemRequest.quantity());
            StoreOutboundItem item = new StoreOutboundItem();
            item.setOrder(order);
            item.setProductSku(itemRequest.productSku());
            item.setProductNameSnapshot(itemRequest.productNameSnapshot());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(defaultMoney(itemRequest.unitPrice()));
            item.setTotalAmount(defaultMoney(itemRequest.unitPrice()).multiply(BigDecimal.valueOf(itemRequest.quantity())));
            order.getItems().add(item);
        }

        StoreOutboundOrder saved = outboundOrderRepository.save(order);
        return new OutboundOrderView(saved.getId(), saved.getOrderNo(), saved.getStatus());
    }

    @Transactional
    public InboundOrderView confirmInbound(Long orderId, String operator) {
        StoreInboundOrder order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException("STORE_INBOUND_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (order.getStatus() != StoreInboundOrder.InboundStatus.DRAFT) {
            throw new CustomException("STORE_INBOUND_ORDER_NOT_CONFIRMABLE", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        for (StoreInboundItem item : order.getItems()) {
            validatePositiveQuantity(item.getQuantity());
            increaseStock(item, order.getOrderNo(), operator, now);
        }

        // 入库确认和库存增加必须在同一事务内完成，避免出现单据已确认但库存未增加。
        order.setStatus(StoreInboundOrder.InboundStatus.CONFIRMED);
        order.setConfirmedBy(operator);
        order.setConfirmedAt(now);
        inboundOrderRepository.save(order);
        return new InboundOrderView(order.getId(), order.getOrderNo(), order.getStatus());
    }

    @Transactional
    public OutboundOrderView confirmOutbound(Long orderId, String operator) {
        StoreOutboundOrder order = outboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException("STORE_OUTBOUND_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (order.getStatus() != StoreOutboundOrder.OutboundStatus.DRAFT) {
            throw new CustomException("STORE_OUTBOUND_ORDER_NOT_CONFIRMABLE", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        for (StoreOutboundItem item : order.getItems()) {
            validatePositiveQuantity(item.getQuantity());
            decreaseStock(item, order.getOrderNo(), operator, now);
        }

        // 出库确认和库存扣减必须在同一事务内完成，库存不足时上面的校验会阻止写流水。
        order.setStatus(StoreOutboundOrder.OutboundStatus.CONFIRMED);
        order.setConfirmedBy(operator);
        order.setConfirmedAt(now);
        outboundOrderRepository.save(order);
        return new OutboundOrderView(order.getId(), order.getOrderNo(), order.getStatus());
    }

    private void increaseStock(StoreInboundItem item, String orderNo, String operator, LocalDateTime now) {
        StoreInventoryStock stock = stockRepository
                .findByProductSkuAndWarehouseCode(item.getProductSku(), DEFAULT_WAREHOUSE_CODE)
                .orElseGet(() -> createEmptyStock(item.getProductSku()));

        // 先保存库存快照，再写对应流水，确保两类记录描述同一组变更前后数量。
        int before = safeQuantity(stock.getCurrentQuantity());
        int after = before + item.getQuantity();
        stock.setCurrentQuantity(after);
        stock.setAvailableQuantity(after);
        stock.setLastInboundAt(now);
        stock.setStatus(resolveStatus(after, stock.getSafeStock()));
        stockRepository.save(stock);

        writeLedger(item.getProductSku(), orderNo, before, item.getQuantity(), after, operator, now,
                StoreInventoryLedger.ChangeType.INBOUND, StoreInventoryLedger.OperationSource.INBOUND_ORDER);
    }

    private void decreaseStock(StoreOutboundItem item, String orderNo, String operator, LocalDateTime now) {
        StoreInventoryStock stock = stockRepository
                .findByProductSkuAndWarehouseCode(item.getProductSku(), DEFAULT_WAREHOUSE_CODE)
                .orElseThrow(() -> new CustomException("STORE_INVENTORY_NOT_ENOUGH", HttpStatus.CONFLICT));

        int before = safeQuantity(stock.getCurrentQuantity());
        // 在修改库存和写流水前拒绝库存不足，依靠外层事务保持整张出库单不变。
        if (before < item.getQuantity()) {
            throw new CustomException("STORE_INVENTORY_NOT_ENOUGH", HttpStatus.CONFLICT);
        }

        int after = before - item.getQuantity();
        stock.setCurrentQuantity(after);
        stock.setAvailableQuantity(after);
        stock.setLastOutboundAt(now);
        stock.setStatus(resolveStatus(after, stock.getSafeStock()));
        stockRepository.save(stock);

        writeLedger(item.getProductSku(), orderNo, before, -item.getQuantity(), after, operator, now,
                StoreInventoryLedger.ChangeType.OUTBOUND, StoreInventoryLedger.OperationSource.OUTBOUND_ORDER);
    }

    private StoreInventoryStock createEmptyStock(String productSku) {
        StoreInventoryStock stock = new StoreInventoryStock();
        stock.setProductSku(productSku);
        stock.setWarehouseCode(DEFAULT_WAREHOUSE_CODE);
        stock.setCurrentQuantity(0);
        stock.setAvailableQuantity(0);
        stock.setSafeStock(0);
        stock.setStatus(StoreInventoryStock.StockStatus.OUT_OF_STOCK);
        return stock;
    }

    private void writeLedger(String productSku,
                             String orderNo,
                             int before,
                             int change,
                             int after,
                             String operator,
                             LocalDateTime now,
                             StoreInventoryLedger.ChangeType changeType,
                             StoreInventoryLedger.OperationSource source) {
        // 流水同时保存带符号的变化量与变更前后快照，便于后续对账和审计。
        StoreInventoryLedger ledger = new StoreInventoryLedger();
        ledger.setProductSku(productSku);
        ledger.setWarehouseCode(DEFAULT_WAREHOUSE_CODE);
        ledger.setChangeType(changeType);
        ledger.setBusinessOrderNo(orderNo);
        ledger.setQuantityBefore(before);
        ledger.setChangeQuantity(change);
        ledger.setQuantityAfter(after);
        ledger.setOperator(operator);
        ledger.setOperatedAt(now);
        ledger.setOperationSource(source);
        ledgerRepository.save(ledger);
    }

    private void validatePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CustomException("STORE_INVENTORY_QUANTITY_INVALID", HttpStatus.BAD_REQUEST);
        }
    }

    private int safeQuantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private StoreInventoryStock.StockStatus resolveStatus(int currentQuantity, Integer safeStock) {
        // 库存风险状态由权威现存量推导，不允许独立编辑造成状态与数量不一致。
        if (currentQuantity <= 0) {
            return StoreInventoryStock.StockStatus.OUT_OF_STOCK;
        }
        if (safeStock != null && currentQuantity < safeStock) {
            return StoreInventoryStock.StockStatus.LOW_STOCK;
        }
        return StoreInventoryStock.StockStatus.NORMAL;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateOrderNo(String prefix) {
        return prefix + "-" + LocalDate.now().format(ORDER_DATE_FORMATTER) + "-" + System.nanoTime();
    }

    private StockView toStockView(StoreInventoryStock stock) {
        return new StockView(
                stock.getId(),
                stock.getProductSku(),
                stock.getWarehouseCode(),
                stock.getCurrentQuantity(),
                stock.getAvailableQuantity(),
                stock.getSafeStock(),
                stock.getStatus()
        );
    }

    public record StockView(
            Long id,
            String productSku,
            String warehouseCode,
            Integer currentQuantity,
            Integer availableQuantity,
            Integer safeStock,
            StoreInventoryStock.StockStatus status
    ) {
    }

    public record InboundOrderCreateRequest(
            StoreInboundOrder.InboundType inboundType,
            String supplier,
            String remark,
            List<InboundItemRequest> items
    ) {
    }

    public record InboundItemRequest(
            String productSku,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal unitPrice
    ) {
    }

    public record OutboundOrderCreateRequest(
            StoreOutboundOrder.OutboundType outboundType,
            String relatedBillNo,
            String remark,
            List<OutboundItemRequest> items
    ) {
    }

    public record OutboundItemRequest(
            String productSku,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal unitPrice
    ) {
    }

    public record InboundOrderView(Long id, String orderNo, StoreInboundOrder.InboundStatus status) {
    }

    public record OutboundOrderView(Long id, String orderNo, StoreOutboundOrder.OutboundStatus status) {
    }
}
