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
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.Map;

/**
 * 提供销售账单历史、修改、Excel 模板和批量导入接口。
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
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.view')")
    public ResponseEntity<?> listBills() {
        return ResponseEntity.ok(success(storeSalesBillService.listBills()));
    }

    @GetMapping("/history")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.view')")
    public ResponseEntity<?> getCustomerHistory(@RequestParam String customerPhone) {
        return ResponseEntity.ok(success(storeSalesBillService.getCustomerHistory(customerPhone)));
    }

    @GetMapping("/{id}/changes")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.view')")
    public ResponseEntity<?> getChanges(@PathVariable Long id) {
        return ResponseEntity.ok(success(storeSalesBillService.getChanges(id)));
    }

    @PostMapping
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.create')")
    public ResponseEntity<?> createBill(
            @RequestBody StoreSalesBillService.SalesBillCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeSalesBillService.createBill(request, operatorResolver.resolve(authentication))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.update')")
    public ResponseEntity<?> updateBill(
            @PathVariable Long id,
            @RequestBody StoreSalesBillService.SalesBillUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(success(storeSalesBillService.updateBill(id, request, operatorResolver.resolve(authentication))));
    }

    @GetMapping("/template")
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.template.download')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = csvService.generateXlsxTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("store-sales-bill-template.xlsx").build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionAuthorization.has(authentication, 'store.sales-bill.import')")
    public ResponseEntity<?> importBills(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            String filename = file.getOriginalFilename();
            Object result = filename != null && filename.toLowerCase().endsWith(".csv")
                    ? csvService.importCsv(file.getInputStream(), operatorResolver.resolve(authentication))
                    : csvService.importXlsx(file.getInputStream(), operatorResolver.resolve(authentication));
            return ResponseEntity.ok(success(result));
        } catch (IOException ex) {
            throw new CustomException("STORE_SALES_BILL_IMPORT_READ_FAILED", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
