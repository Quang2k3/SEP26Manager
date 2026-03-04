package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/health")
@Tag(name = "Health Check", description = "Kiểm tra trạng thái hoạt động của hệ thống. Không cần authentication.")
public class HealthController {

    @GetMapping
    @Operation(summary = "Health check", description = "Kiểm tra hệ thống có đang hoạt động hay không. Trả về status, timestamp, application name.")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("application", "Warehouse Management System");

        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    @Operation(summary = "Thông tin hệ thống", description = "Trả về tên ứng dụng và phiên bản hiện tại.")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> info = new HashMap<>();
        info.put("application", "Warehouse Management System");
        info.put("version", "1.0.0");

        return ResponseEntity.ok(info);
    }
}