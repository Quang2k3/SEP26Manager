package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateCategoryZoneMappingRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CategoryZoneMappingResponse;
import org.example.sep26management.application.service.CategoryZoneMappingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Category-Zone Mapping Management
 * MANAGER only - maps categories to zones for storage organization
 */
@RestController
@RequestMapping("/api/v1/category-zone-mappings")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MANAGER')")
public class CategoryZoneMappingController {

    private final CategoryZoneMappingService mappingService;

    /**
     * Get current user ID from security context
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            return Long.parseLong(email.split("@")[0]);
        }
        return 1L;
    }

    /**
     * Create category-zone mapping
     * POST /api/v1/category-zone-mappings
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryZoneMappingResponse>> createMapping(
            @Valid @RequestBody CreateCategoryZoneMappingRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId();
        log.info("Creating mapping: Category {} → Zone {} by user {}",
                request.getCategoryId(), request.getZoneId(), currentUserId);

        ApiResponse<CategoryZoneMappingResponse> response = mappingService.createMapping(request, currentUserId);

        if (Boolean.FALSE.equals(response.getSuccess())) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all category-zone mappings
     * GET /api/v1/category-zone-mappings
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryZoneMappingResponse>>> getAllMappings() {
        log.info("Fetching all category-zone mappings");

        ApiResponse<List<CategoryZoneMappingResponse>> response = mappingService.getAllMappings();
        return ResponseEntity.ok(response);
    }

    /**
     * Get mappings by category
     * GET /api/v1/category-zone-mappings/category/{categoryId}
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<CategoryZoneMappingResponse>>> getMappingsByCategory(
            @PathVariable Long categoryId) {
        log.info("Fetching mappings for category: {}", categoryId);

        ApiResponse<List<CategoryZoneMappingResponse>> response = mappingService.getMappingsByCategory(categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get mappings by zone
     * GET /api/v1/category-zone-mappings/zone/{zoneId}
     */
    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<ApiResponse<List<CategoryZoneMappingResponse>>> getMappingsByZone(
            @PathVariable Long zoneId) {
        log.info("Fetching mappings for zone: {}", zoneId);

        ApiResponse<List<CategoryZoneMappingResponse>> response = mappingService.getMappingsByZone(zoneId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete category-zone mapping
     * DELETE /api/v1/category-zone-mappings/category/{categoryId}/zone/{zoneId}
     */
    @DeleteMapping("/category/{categoryId}/zone/{zoneId}")
    public ResponseEntity<ApiResponse<String>> deleteMapping(
            @PathVariable Long categoryId,
            @PathVariable Long zoneId,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId();
        log.info("Deleting mapping: Category {} → Zone {} by user {}", categoryId, zoneId, currentUserId);

        ApiResponse<String> response = mappingService.deleteMapping(categoryId, zoneId, currentUserId);
        return ResponseEntity.ok(response);
    }
}
