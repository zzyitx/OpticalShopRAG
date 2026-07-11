package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreInventoryLedger;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.model.StoreProduct;
import com.yizhaoqi.smartpai.model.StoreSalesBill;
import com.yizhaoqi.smartpai.repository.StoreInventoryLedgerRepository;
import com.yizhaoqi.smartpai.repository.StoreInventoryStockRepository;
import com.yizhaoqi.smartpai.repository.StoreProductRepository;
import com.yizhaoqi.smartpai.repository.StoreSalesBillRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only business query facade for AI tools and future APIs.
 */
@Service
public class StoreQueryService {

    static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    private static final int DEFAULT_DATE_DAYS = 30;
    private static final int MAX_DATE_DAYS = 366;

    private final StoreProductRepository productRepository;
    private final StoreInventoryStockRepository stockRepository;
    private final StoreInventoryLedgerRepository ledgerRepository;
    private final StoreSalesBillRepository salesBillRepository;

    public StoreQueryService(StoreProductRepository productRepository,
                             StoreInventoryStockRepository stockRepository,
                             StoreInventoryLedgerRepository ledgerRepository,
                             StoreSalesBillRepository salesBillRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.ledgerRepository = ledgerRepository;
        this.salesBillRepository = salesBillRepository;
    }

    @Transactional(readOnly = true)
    public QueryResult<ProductView> queryProducts(ProductQuery query) {
        int limit = resolveLimit(query == null ? null : query.limit());
        List<ProductView> products = productRepository.searchProducts(
                        trimToNull(query == null ? null : query.sku()),
                        trimToNull(query == null ? null : query.keyword()),
                        trimToNull(query == null ? null : query.brand()),
                        trimToNull(query == null ? null : query.model()),
                        query == null ? null : query.category(),
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(this::toProductView)
                .toList();
        return result("store_product", "商品档案", products, limit, this::formatProduct);
    }

    @Transactional(readOnly = true)
    public QueryResult<InventoryView> queryInventory(InventoryQuery query) {
        int limit = resolveLimit(query == null ? null : query.limit());
        boolean warningOnly = query != null && Boolean.TRUE.equals(query.warningOnly());
        List<StoreInventoryStock> stocks = stockRepository.searchStocks(
                trimToNull(query == null ? null : query.sku()),
                query == null ? null : query.status(),
                warningOnly,
                PageRequest.of(0, limit)
        );
        Map<String, StoreProduct> productsBySku = findProductsBySku(stocks.stream()
                .map(StoreInventoryStock::getProductSku)
                .distinct()
                .toList());
        List<InventoryView> inventory = stocks.stream()
                .map(stock -> toInventoryView(stock, productsBySku.get(stock.getProductSku())))
                .toList();
        return result("store_inventory_stock", "实时库存", inventory, limit, this::formatInventory);
    }

    @Transactional(readOnly = true)
    public QueryResult<StockFlowView> queryStockFlows(StockFlowQuery query) {
        DateRange range = resolveDateRange(query == null ? null : query.startDate(), query == null ? null : query.endDate());
        int limit = resolveLimit(query == null ? null : query.limit());
        List<StockFlowView> flows = ledgerRepository.searchLedgers(
                        trimToNull(query == null ? null : query.sku()),
                        trimToNull(query == null ? null : query.businessOrderNo()),
                        query == null ? null : query.changeType(),
                        range.startDate().atStartOfDay(),
                        range.endDate().plusDays(1).atStartOfDay(),
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(this::toStockFlowView)
                .toList();
        return result("store_inventory_ledger", "库存流水", flows, limit, this::formatStockFlow);
    }

    @Transactional(readOnly = true)
    public QueryResult<SalesBillView> querySalesBills(SalesBillQuery query) {
        String customerPhone = trimToNull(query == null ? null : query.customerPhone());
        String customerName = trimToNull(query == null ? null : query.customerName());
        String billNo = trimToNull(query == null ? null : query.billNo());
        if (!StringUtils.hasText(customerPhone) && !StringUtils.hasText(billNo)) {
            throw new CustomException("STORE_QUERY_CUSTOMER_PHONE_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        DateRange range = resolveDateRange(query == null ? null : query.startDate(), query == null ? null : query.endDate());
        int limit = resolveLimit(query == null ? null : query.limit());
        List<SalesBillView> bills = salesBillRepository.searchBills(
                        customerPhone,
                        customerName,
                        billNo,
                        range.startDate(),
                        range.endDate(),
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(this::toSalesBillView)
                .toList();
        return result("store_sales_bill", "销售账单", bills, limit, this::formatSalesBill);
    }

    @Transactional(readOnly = true)
    public QueryResult<StoreStatsView> queryStoreStats(StoreStatsQuery query) {
        DateRange range = resolveDateRange(query == null ? null : query.startDate(), query == null ? null : query.endDate());
        StoreStatsView stats = new StoreStatsView(
                range.startDate(),
                range.endDate(),
                salesBillRepository.countByPurchaseDateBetween(range.startDate(), range.endDate()),
                defaultMoney(salesBillRepository.sumActualAmountByPurchaseDateBetween(range.startDate(), range.endDate())),
                stockRepository.countByStatus(StoreInventoryStock.StockStatus.LOW_STOCK),
                stockRepository.countByStatus(StoreInventoryStock.StockStatus.OUT_OF_STOCK),
                trimToNull(query == null ? null : query.dimension()) == null ? "summary" : query.dimension().trim()
        );
        return result("store_business_stats", "门店经营统计", List.of(stats), 1, false, this::formatStoreStats);
    }

    private <T> QueryResult<T> result(String source,
                                      String sourceLabel,
                                      List<T> records,
                                      int limit,
                                      Function<T, String> formatter) {
        List<T> safeRecords = records == null ? List.of() : records;
        return result(source, sourceLabel, safeRecords, limit, !safeRecords.isEmpty() && safeRecords.size() >= limit, formatter);
    }

    private <T> QueryResult<T> result(String source,
                                      String sourceLabel,
                                      List<T> records,
                                      int limit,
                                      boolean truncated,
                                      Function<T, String> formatter) {
        List<T> safeRecords = records == null ? List.of() : records;
        StringBuilder content = new StringBuilder("来源：").append(sourceLabel);
        if (safeRecords.isEmpty()) {
            content.append("\n未查询到记录。");
        } else {
            for (int i = 0; i < safeRecords.size(); i++) {
                content.append("\n").append(i + 1).append(". ").append(formatter.apply(safeRecords.get(i)));
            }
        }
        // 当前不额外执行 count 查询，truncated 表示结果可能被查询上限截断。
        return new QueryResult<>(
                source,
                sourceLabel,
                safeRecords,
                safeRecords,
                content.toString(),
                safeRecords.size(),
                limit,
                truncated
        );
    }

    private ProductView toProductView(StoreProduct product) {
        return new ProductView(
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getSpecification(),
                product.getRetailPrice(),
                product.getSafeStock(),
                product.getStatus()
        );
    }

    private InventoryView toInventoryView(StoreInventoryStock stock, StoreProduct product) {
        return new InventoryView(
                stock.getProductSku(),
                product == null ? null : product.getName(),
                stock.getWarehouseCode(),
                stock.getCurrentQuantity(),
                stock.getAvailableQuantity(),
                stock.getSafeStock(),
                stock.getStatus(),
                stock.getLastInboundAt(),
                stock.getLastOutboundAt()
        );
    }

    private StockFlowView toStockFlowView(StoreInventoryLedger ledger) {
        return new StockFlowView(
                ledger.getProductSku(),
                ledger.getWarehouseCode(),
                ledger.getChangeType(),
                ledger.getBusinessOrderNo(),
                ledger.getQuantityBefore(),
                ledger.getChangeQuantity(),
                ledger.getQuantityAfter(),
                ledger.getOperationSource(),
                ledger.getOperator(),
                ledger.getOperatedAt()
        );
    }

    private SalesBillView toSalesBillView(StoreSalesBill bill) {
        return new SalesBillView(
                bill.getBillNo(),
                bill.getCustomerName(),
                bill.getCustomerPhone(),
                bill.getPurchaseDate(),
                formatEyeDegree(bill.getLeftMyopiaDegree(), bill.getLeftAstigmatism(), bill.getLeftAxis()),
                formatEyeDegree(bill.getRightMyopiaDegree(), bill.getRightAstigmatism(), bill.getRightAxis()),
                bill.getPupillaryDistance(),
                bill.getFrameModel(),
                bill.getLensModel(),
                bill.getPaymentAmount(),
                bill.getDiscountAmount(),
                bill.getActualAmount(),
                bill.getPaymentMethod(),
                bill.getSalesperson(),
                bill.getOptometrist()
        );
    }

    private Map<String, StoreProduct> findProductsBySku(List<String> skuList) {
        if (skuList == null || skuList.isEmpty()) {
            return Map.of();
        }
        return productRepository.findBySkuIn(skuList).stream()
                .collect(Collectors.toMap(StoreProduct::getSku, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(DEFAULT_DATE_DAYS - 1L) : startDate;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new CustomException("STORE_QUERY_DATE_RANGE_INVALID", HttpStatus.BAD_REQUEST);
        }
        if (resolvedStart.plusDays(MAX_DATE_DAYS - 1L).isBefore(resolvedEnd)) {
            throw new CustomException("STORE_QUERY_DATE_RANGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String formatProduct(ProductView product) {
        return product.sku() + " " + product.name()
                + "，品牌：" + nullToDash(product.brand())
                + "，型号：" + nullToDash(product.model())
                + "，零售价：" + defaultMoney(product.retailPrice())
                + "，状态：" + product.status();
    }

    private String formatInventory(InventoryView inventory) {
        return inventory.productSku() + " " + nullToDash(inventory.productName())
                + "，现存：" + inventory.currentQuantity()
                + "，可用：" + inventory.availableQuantity()
                + "，安全库存：" + inventory.safeStock()
                + "，状态：" + inventory.status();
    }

    private String formatStockFlow(StockFlowView flow) {
        return flow.productSku()
                + "，单号：" + flow.businessOrderNo()
                + "，类型：" + flow.changeType()
                + "，变化：" + flow.quantityBefore() + " -> " + flow.quantityAfter()
                + "（" + flow.changeQuantity() + "）"
                + "，时间：" + flow.operatedAt();
    }

    private String formatSalesBill(SalesBillView bill) {
        return bill.billNo()
                + "，客户：" + bill.customerName()
                + "，手机号：" + bill.customerPhone()
                + "，日期：" + bill.purchaseDate()
                + "，左眼：" + bill.leftDegreeDisplay()
                + "，右眼：" + bill.rightDegreeDisplay()
                + "，实收：" + defaultMoney(bill.actualAmount());
    }

    private String formatStoreStats(StoreStatsView stats) {
        return stats.startDate() + " 至 " + stats.endDate()
                + "，账单数：" + stats.salesBillCount()
                + "，实收金额：" + defaultMoney(stats.actualAmount())
                + "，低库存商品：" + stats.lowStockCount()
                + "，缺货商品：" + stats.outOfStockCount();
    }

    private String formatEyeDegree(BigDecimal myopia, BigDecimal astigmatism, Integer axis) {
        return formatDecimal(myopia) + "-" + formatDecimal(astigmatism) + "-" + (axis == null ? "" : axis);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
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

    private String nullToDash(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    public record ProductQuery(
            String sku,
            String keyword,
            String brand,
            String model,
            StoreProduct.ProductCategory category,
            Integer limit
    ) {
    }

    public record InventoryQuery(
            String sku,
            StoreInventoryStock.StockStatus status,
            Boolean warningOnly,
            Integer limit
    ) {
    }

    public record StockFlowQuery(
            String sku,
            String businessOrderNo,
            StoreInventoryLedger.ChangeType changeType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit
    ) {
    }

    public record SalesBillQuery(
            String customerPhone,
            String customerName,
            String billNo,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit
    ) {
    }

    public record StoreStatsQuery(
            LocalDate startDate,
            LocalDate endDate,
            String dimension
    ) {
    }

    public record QueryResult<T>(
            String source,
            String sourceLabel,
            List<T> data,
            List<T> records,
            String content,
            int recordCount,
            int limit,
            boolean truncated
    ) {
    }

    public record ProductView(
            String sku,
            String name,
            StoreProduct.ProductCategory category,
            String brand,
            String model,
            String specification,
            BigDecimal retailPrice,
            Integer safeStock,
            StoreProduct.ProductStatus status
    ) {
    }

    public record InventoryView(
            String productSku,
            String productName,
            String warehouseCode,
            Integer currentQuantity,
            Integer availableQuantity,
            Integer safeStock,
            StoreInventoryStock.StockStatus status,
            LocalDateTime lastInboundAt,
            LocalDateTime lastOutboundAt
    ) {
    }

    public record StockFlowView(
            String productSku,
            String warehouseCode,
            StoreInventoryLedger.ChangeType changeType,
            String businessOrderNo,
            Integer quantityBefore,
            Integer changeQuantity,
            Integer quantityAfter,
            StoreInventoryLedger.OperationSource operationSource,
            String operator,
            LocalDateTime operatedAt
    ) {
    }

    public record SalesBillView(
            String billNo,
            String customerName,
            String customerPhone,
            LocalDate purchaseDate,
            String leftDegreeDisplay,
            String rightDegreeDisplay,
            BigDecimal pupillaryDistance,
            String frameModel,
            String lensModel,
            BigDecimal paymentAmount,
            BigDecimal discountAmount,
            BigDecimal actualAmount,
            StoreSalesBill.PaymentMethod paymentMethod,
            String salesperson,
            String optometrist
    ) {
    }

    public record StoreStatsView(
            LocalDate startDate,
            LocalDate endDate,
            long salesBillCount,
            BigDecimal actualAmount,
            long lowStockCount,
            long outOfStockCount,
            String dimension
    ) {
    }
}
