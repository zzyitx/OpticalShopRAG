package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.StoreDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供门店经营台使用的看板摘要接口。
 */
@RestController
@RequestMapping("/api/v1/store/dashboard")
public class StoreDashboardController {

    private final StoreDashboardService storeDashboardService;

    public StoreDashboardController(StoreDashboardService storeDashboardService) {
        this.storeDashboardService = storeDashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary() {
        return ResponseEntity.ok(success(storeDashboardService.getSummary()));
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }
}
