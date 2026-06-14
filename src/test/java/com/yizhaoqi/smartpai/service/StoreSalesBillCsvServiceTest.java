package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.StoreSalesBill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreSalesBillCsvServiceTest {

    private StoreSalesBillService salesBillService;
    private StoreSalesBillCsvService service;

    @BeforeEach
    void setUp() {
        salesBillService = Mockito.mock(StoreSalesBillService.class);
        service = new StoreSalesBillCsvService(salesBillService);
    }

    @Test
    void shouldGenerateTemplateWithRequiredHeaders() {
        String template = service.generateTemplate();

        assertTrue(template.startsWith("customerName,customerPhone,purchaseDate"));
        assertTrue(template.contains("leftMyopiaDegree,leftAstigmatism,leftAxis"));
    }

    @Test
    void shouldImportRowsThroughSalesBillService() {
        when(salesBillService.createBill(any(StoreSalesBillService.SalesBillCreateRequest.class), eq("admin")))
                .thenReturn(new StoreSalesBillService.SalesBillView(
                        1L,
                        "SB-001",
                        "Alice",
                        "13800000000",
                        null,
                        null,
                        null,
                        null,
                        "",
                        null,
                        null,
                        null,
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        StoreSalesBill.PaymentMethod.CASH,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.List.of()
                ));
        String csv = service.generateTemplate()
                + "Alice,13800000000,2026-06-08,-3.50,-0.75,180,-2.75,-0.50,170,62.00,FRAME-A123,LENS-B456,399.00,0.00,399.00,CASH,Lee,Wang,first bill\n"
                + "Bob,13900000000,2026-06-08,-1.25,0,0,-1.00,0,0,63.00,FRAME-B123,LENS-C456,299.00,20.00,279.00,WECHAT,Lee,Wang,second bill\n";

        StoreSalesBillCsvService.ImportResult result = service.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "admin"
        );

        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        verify(salesBillService, times(2)).createBill(any(StoreSalesBillService.SalesBillCreateRequest.class), eq("admin"));
    }

    @Test
    void shouldGenerateAndImportXlsxTemplate() {
        when(salesBillService.createBill(any(StoreSalesBillService.SalesBillCreateRequest.class), eq("admin")))
                .thenReturn(null);
        byte[] template = service.generateXlsxTemplate();

        StoreSalesBillCsvService.ImportResult result = service.importXlsx(new ByteArrayInputStream(template), "admin");

        assertTrue(template.length > 0);
        assertEquals(0, result.successCount());
        assertEquals(0, result.failureCount());
    }
}
