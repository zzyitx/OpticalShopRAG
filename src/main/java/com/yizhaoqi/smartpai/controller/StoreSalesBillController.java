package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.service.StoreSalesBillCsvService;
import com.yizhaoqi.smartpai.service.StoreSalesBillService;
import com.yizhaoqi.smartpai.service.StoreOperatorResolver;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 提供销售账单历史、修改、CSV 模板和 CSV 导入接口。
 */
@RestController
@RequestMapping("/api/v1/store/sales-bills")
public class StoreSalesBillController {

    private final StoreSalesBillService storeSalesBillService;
    private final StoreSalesBillCsvService csvService;
    private final StoreOperatorResolver operatorResolver;

    public StoreSalesBillController(StoreSalesBillService storeSalesBillService,
                                    StoreSalesBillCsvService csvService,
                                    StoreOperatorResolver operatorResolver) {
        this.storeSalesBillService = storeSalesBillService;
        this.csvService = csvService;
        this.operatorResolver = operatorResolver;
    }

    @GetMapping
    public ResponseEntity<?> listBills() {
        return ResponseEntity.ok(success(storeSalesBillService.listBills()));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getCustomerHistory(@RequestParam String customerPhone) {
        return ResponseEntity.ok(success(storeSalesBillService.getCustomerHistory(customerPhone)));
    }

    @PostMapping
    public ResponseEntity<?> createBill(
            @RequestBody StoreSalesBillService.SalesBillCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeSalesBillService.createBill(request, operatorResolver.resolve(authentication))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBill(
            @PathVariable Long id,
            @RequestBody StoreSalesBillService.SalesBillUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeSalesBillService.updateBill(id, request, operatorResolver.resolve(authentication))));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = csvService.generateTemplate().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("store-sales-bill-template.csv").build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importBills(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            return ResponseEntity.ok(success(csvService.importCsv(file.getInputStream(), operatorResolver.resolve(authentication))));
        } catch (IOException ex) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_READ_FAILED", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
