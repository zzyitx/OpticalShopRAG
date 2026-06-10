package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.StoreSalesBill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成销售账单 CSV 模板，并通过权威账单服务逐行导入数据。
 */
@Service
public class StoreSalesBillCsvService {

    private static final String TEMPLATE_HEADER = String.join(",",
            "customerName",
            "customerPhone",
            "purchaseDate",
            "leftMyopiaDegree",
            "leftAstigmatism",
            "leftAxis",
            "rightMyopiaDegree",
            "rightAstigmatism",
            "rightAxis",
            "pupillaryDistance",
            "frameModel",
            "lensModel",
            "paymentAmount",
            "discountAmount",
            "actualAmount",
            "paymentMethod",
            "salesperson",
            "optometrist",
            "remark"
    );

    private final StoreSalesBillService salesBillService;

    public StoreSalesBillCsvService(StoreSalesBillService salesBillService) {
        this.salesBillService = salesBillService;
    }

    public String generateTemplate() {
        return TEMPLATE_HEADER + "\n";
    }

    public ImportResult importCsv(InputStream inputStream, String operator) {
        if (inputStream == null) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        int successCount = 0;
        List<ImportRowError> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            validateHeader(header);

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                try {
                    StoreSalesBillService.SalesBillCreateRequest request = toCreateRequest(parseCsvLine(line), rowNumber);
                    // 导入仍复用销售单创建服务，确保必填、金额默认值和客户历史规则只有一个权威入口。
                    salesBillService.createBill(request, operator);
                    successCount++;
                } catch (RuntimeException ex) {
                    // 单行错误只记录到导入报告，不丢弃同一文件中已经成功导入的有效行。
                    errors.add(new ImportRowError(rowNumber, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_READ_FAILED", HttpStatus.BAD_REQUEST);
        }

        return new ImportResult(successCount, errors.size(), List.copyOf(errors));
    }

    private void validateHeader(String header) {
        if (!TEMPLATE_HEADER.equals(header)) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_HEADER_INVALID", HttpStatus.BAD_REQUEST);
        }
    }

    private StoreSalesBillService.SalesBillCreateRequest toCreateRequest(List<String> columns, int rowNumber) {
        if (columns.size() != 19) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_COLUMN_COUNT_INVALID_ROW_" + rowNumber, HttpStatus.BAD_REQUEST);
        }

        return new StoreSalesBillService.SalesBillCreateRequest(
                columns.get(0),
                columns.get(1),
                parseDate(columns.get(2)),
                parseDecimal(columns.get(3)),
                parseDecimal(columns.get(4)),
                parseInteger(columns.get(5)),
                parseDecimal(columns.get(6)),
                parseDecimal(columns.get(7)),
                parseInteger(columns.get(8)),
                parseDecimal(columns.get(9)),
                columns.get(10),
                columns.get(11),
                parseDecimal(columns.get(12)),
                parseDecimal(columns.get(13)),
                parseDecimal(columns.get(14)),
                parsePaymentMethod(columns.get(15)),
                columns.get(16),
                columns.get(17),
                columns.get(18)
        );
    }

    private List<String> parseCsvLine(String line) {
        // 按 CSV 规则处理引号内逗号和双引号转义，避免直接按逗号切分破坏字段。
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);
            if (currentChar == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (currentChar == ',' && !quoted) {
                columns.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        columns.add(current.toString().trim());
        return columns;
    }

    private LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private BigDecimal parseDecimal(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : null;
    }

    private Integer parseInteger(String value) {
        return StringUtils.hasText(value) ? Integer.valueOf(value.trim()) : null;
    }

    private StoreSalesBill.PaymentMethod parsePaymentMethod(String value) {
        return StringUtils.hasText(value) ? StoreSalesBill.PaymentMethod.valueOf(value.trim()) : null;
    }

    public record ImportResult(
            int successCount,
            int failureCount,
            List<ImportRowError> errors
    ) {
    }

    public record ImportRowError(
            int rowNumber,
            String message
    ) {
    }
}
