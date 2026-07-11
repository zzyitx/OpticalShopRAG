package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.model.StoreInventoryStock;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    private StoreQueryService storeQueryService;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        storeQueryService = Mockito.mock(StoreQueryService.class);
        registry = new AgentToolRegistry(
                Mockito.mock(HybridSearchService.class),
                Mockito.mock(DeepSeekClient.class),
                Mockito.mock(StringRedisTemplate.class),
                Mockito.mock(ElasticsearchClient.class),
                Mockito.mock(FileUploadRepository.class),
                storeQueryService
        );
    }

    @Test
    void shouldRegisterReadOnlyStoreQueryTools() {
        List<String> names = registry.getTools().stream()
                .map(AgentToolRegistry.AgentTool::name)
                .toList();

        assertTrue(names.contains("query_product"));
        assertTrue(names.contains("query_inventory"));
        assertTrue(names.contains("query_stock_flow"));
        assertTrue(names.contains("query_sales_bill"));
        assertTrue(names.contains("query_store_stats"));
    }

    @Test
    void shouldExecuteInventoryToolThroughStoreQueryService() {
        StoreQueryService.InventoryView inventory = new StoreQueryService.InventoryView(
                "FRAME-A123",
                "A123 frame",
                StoreInventoryService.DEFAULT_WAREHOUSE_CODE,
                3,
                3,
                5,
                StoreInventoryStock.StockStatus.LOW_STOCK,
                null,
                null
        );
        StoreQueryService.QueryResult<StoreQueryService.InventoryView> queryResult =
                new StoreQueryService.QueryResult<>(
                        "store_inventory_stock",
                        "实时库存",
                        List.of(inventory),
                        List.of(inventory),
                        "来源：实时库存\n1. FRAME-A123 A123 frame",
                        1,
                        1,
                        true
                );
        when(storeQueryService.queryInventory(any(StoreQueryService.InventoryQuery.class))).thenReturn(queryResult);

        AgentToolRegistry.ToolExecutionResult result = registry.executeTool(
                "query_inventory",
                Map.of("sku", "FRAME-A123", "warningOnly", true, "limit", 100),
                "admin"
        );

        assertEquals("query_inventory", result.toolName());
        assertEquals("来源：实时库存\n1. FRAME-A123 A123 frame", result.content());
        assertEquals("store_inventory_stock", result.data().get("source"));
        assertEquals("实时库存", result.data().get("sourceLabel"));
        assertEquals(List.of(inventory), result.data().get("records"));
        assertEquals(1, result.data().get("recordCount"));
        assertEquals(1, result.data().get("limit"));
        assertEquals(true, result.data().get("truncated"));
        ArgumentCaptor<StoreQueryService.InventoryQuery> queryCaptor =
                ArgumentCaptor.forClass(StoreQueryService.InventoryQuery.class);
        verify(storeQueryService).queryInventory(queryCaptor.capture());
        assertEquals("FRAME-A123", queryCaptor.getValue().sku());
        assertTrue(queryCaptor.getValue().warningOnly());
        assertEquals(100, queryCaptor.getValue().limit());
    }
}
