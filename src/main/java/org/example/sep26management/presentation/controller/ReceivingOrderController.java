package org.example.sep26management.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ReceivingOrderResponse;
import org.example.sep26management.application.service.ReceivingOrderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/receiving-orders")
@RequiredArgsConstructor
public class ReceivingOrderController {

    private final ReceivingOrderService receivingOrderService;

    /** GET /v1/receiving-orders?status=SUBMITTED */
    @GetMapping
    public ApiResponse<List<ReceivingOrderResponse>> list(@RequestParam(required = false) String status) {
        return receivingOrderService.listOrders(status);
    }

    /** GET /v1/receiving-orders/{id} */
    @GetMapping("/{id}")
    public ApiResponse<ReceivingOrderResponse> get(@PathVariable Long id) {
        return receivingOrderService.getOrder(id);
    }

    /** POST /v1/receiving-orders/{id}/submit — Keeper */
    @PostMapping("/{id}/submit")
    public ApiResponse<ReceivingOrderResponse> submit(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.submit(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/approve — Manager */
    @PostMapping("/{id}/approve")
    public ApiResponse<ReceivingOrderResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.approve(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/post — Accountant */
    @PostMapping("/{id}/post")
    public ApiResponse<ReceivingOrderResponse> post(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.post(id, extractUserId(auth));
    }

    @SuppressWarnings("unchecked")
    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object uid = ((Map<?, ?>) auth.getDetails()).get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
        }
        throw new RuntimeException("Cannot extract userId from authentication");
    }
}
