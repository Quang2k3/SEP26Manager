package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.infrastructure.mapper.SkuMapper;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkuService {

    private final SkuJpaRepository skuJpaRepository;
    private final SkuMapper skuMapper;

    /**
     * Lookup SKU by barcode.
     * Used by the barcode scan flow (iPhone sends barcode → server resolves SKU).
     *
     * @param barcode barcode string scanned from device
     * @return ApiResponse containing SkuResponse if found
     */
    @Transactional(readOnly = true)
    public ApiResponse<SkuResponse> findByBarcode(String barcode) {
        log.info("Looking up SKU by barcode: {}", barcode);

        return skuJpaRepository.findActiveByBarcodeWithCategory(barcode)
                .map(sku -> {
                    log.info("SKU found for barcode {}: skuCode={}", barcode, sku.getSkuCode());
                    return ApiResponse.success("SKU found", skuMapper.toResponse(sku));
                })
                .orElseGet(() -> {
                    log.warn("No active SKU found for barcode: {}", barcode);
                    return ApiResponse.error("SKU not found for barcode: " + barcode);
                });
    }

    /**
     * Lookup SKU by SKU code.
     *
     * @param skuCode the SKU code
     * @return ApiResponse containing SkuResponse if found
     */
    @Transactional(readOnly = true)
    public ApiResponse<SkuResponse> findBySkuCode(String skuCode) {
        log.info("Looking up SKU by skuCode: {}", skuCode);

        return skuJpaRepository.findActiveBySkuCodeWithCategory(skuCode)
                .map(sku -> {
                    log.info("SKU found: skuCode={}", sku.getSkuCode());
                    return ApiResponse.success("SKU found", skuMapper.toResponse(sku));
                })
                .orElseGet(() -> {
                    log.warn("No active SKU found for skuCode: {}", skuCode);
                    return ApiResponse.error("SKU not found: " + skuCode);
                });
    }
}
