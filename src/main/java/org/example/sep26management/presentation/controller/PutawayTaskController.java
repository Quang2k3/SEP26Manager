package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.PutawayConfirmRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.application.service.PutawayTaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/putaway-tasks")
@RequiredArgsConstructor
@Tag(name = "Putaway Tasks", description = "Quản lý nhiệm vụ xếp hàng lên kệ (Putaway). "
        + "Putaway task được tự động tạo khi GRN posted. Mỗi task chứa danh sách items cần xếp, "
        + "kèm gợi ý vị trí (suggested location) dựa trên zone-category matching. "
        + "Keeper xem gợi ý → quét kệ → xác nhận putaway.")
public class PutawayTaskController {

    private final PutawayTaskService putawayTaskService;

    /**
     * GET /v1/putaway-tasks?assignedTo=me&status=OPEN
     * Keeper fetches their task list.
     */
    @GetMapping
    @Operation(summary = "Danh sách putaway tasks", description = "Lấy danh sách putaway tasks. Lọc theo assignedTo (userId) và/hoặc status (OPEN, IN_PROGRESS, DONE).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.assignedTo` (Tùy chọn): Lọc theo ID nhân viên thực hiện.\n"
            + "- `Query.status` (Tùy chọn): Lọc theo trạng thái.\n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định 10.")
    public ApiResponse<PageResponse<PutawayTaskResponse>> list(
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return putawayTaskService.listTasks(assignedTo, status, page, size);
    }

    /** GET /v1/putaway-tasks/{id} — detail with items */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết putaway task", description = "Lấy chi tiết 1 putaway task. Response bao gồm danh sách items với: "
            + "suggestedLocationCode (bin gợi ý), suggestedZoneCode, suggestedAisle, suggestedRack, "
            + "binCurrentQty, binMaxCapacity, binAvailableCapacity. "
            + "Nếu suggestedLocationId = staging → chưa có gợi ý (zone/bin không tìm thấy).")
    public ApiResponse<PutawayTaskResponse> get(@PathVariable Long id) {
        return putawayTaskService.getTask(id);
    }

    /**
     * GET /v1/putaway-tasks/{id}/suggestions
     * Get putaway suggestions for all items in a task (zone-category matching).
     */
    @GetMapping("/{id}/suggestions")
    @Operation(summary = "Gợi ý vị trí putaway cho task", description = "Tính toán và trả về gợi ý vị trí putaway cho tất cả items trong task. "
            + "Logic: SKU → Category (categoryCode) → Zone (Z-{categoryCode}) → BIN có dung lượng trống nhất. "
            + "Ví dụ: SKU thuộc category 'HC' → gợi ý BIN trong zone 'Z-HC'. "
            + "Response gồm: matchedZoneCode, suggestedLocationCode, aisleName, rackName, currentQty, maxCapacity, availableCapacity.")
    public ApiResponse<List<PutawaySuggestion>> getSuggestions(@PathVariable Long id) {
        return putawayTaskService.getSuggestions(id);
    }

    /**
     * POST /v1/putaway-tasks/{id}/confirm
     * Keeper scans shelf and assigns actual location + qty for each item.
     */
    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận putaway (Keeper)", description = "Keeper quét kệ và xác nhận vị trí + số lượng thực tế cho mỗi item. "
            + "Hệ thống: giảm inventory tại staging, tăng inventory tại location đích, ghi transaction PUTAWAY. "
            + "Nếu tất cả items đã putaway đủ qty → task chuyển sang DONE.")
    public ApiResponse<PutawayTaskResponse> confirm(
            @PathVariable Long id,
            @Valid @RequestBody PutawayConfirmRequest request,
            Authentication auth) {
        return putawayTaskService.confirm(id, request, extractUserId(auth));
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
