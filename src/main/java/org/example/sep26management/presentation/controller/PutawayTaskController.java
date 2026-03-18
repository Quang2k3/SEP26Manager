package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.PutawayAllocateRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawayAllocationResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.application.service.PutawayTaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/putaway-tasks")
@RequiredArgsConstructor
@Tag(name = "Putaway Tasks", description = "Quản lý nhiệm vụ xếp hàng lên kệ (Putaway). "
        + "Flow: Xem task detail → Allocate (đặt chỗ) items vào bins → Confirm (xác nhận thực tế).")
public class PutawayTaskController {

    private final PutawayTaskService putawayTaskService;

    // ─── CRUD ────────────────────────────────────────────────────────────────────

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
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long warehouseId = extractWarehouseId(auth);
        return putawayTaskService.listTasks(warehouseId, assignedTo, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết putaway task", description = "Xem chi tiết task: danh sách items với `quantity` (tổng), `putawayQty` (đã cất), `allocatedQty` (đã đặt chỗ), `remainingQty` (còn lại chưa phân bổ).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã **Putaway Task ID**.")
    public ApiResponse<PutawayTaskResponse> get(@PathVariable Long id) {
        return putawayTaskService.getTask(id);
    }

    @GetMapping("/grn/{grnId}")
    @Operation(summary = "Lấy putaway task theo GRN", description = "Lấy Putaway Task được sinh tự động từ GRN.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable grnId`: Mã GRN ID.")
    public ApiResponse<PutawayTaskResponse> getByGrnId(@PathVariable Long grnId) {
        return putawayTaskService.getTaskByGrnId(grnId);
    }

    @GetMapping("/{id}/suggestions")
    @Operation(summary = "Gợi ý vị trí cất hàng (AI)", description = "Xem AI suggestions cho tất cả items trong task.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Putaway Task ID.")
    public ApiResponse<List<PutawaySuggestion>> getSuggestions(@PathVariable Long id) {
        return putawayTaskService.getSuggestions(id);
    }

    // ─── Allocate (Reserve) ──────────────────────────────────────────────────────

    @PostMapping("/{id}/allocate")
    @Operation(summary = "Đặt chỗ hàng vào bin (Reserve)", description = "Keeper chọn bin và phân bổ hàng vào đó. "
            + "Hệ thống lock capacity của bin → keeper khác sẽ không chiếm được.\n\n"
            + "**Flow:**\n"
            + "1. `GET /v1/bins/occupancy?zoneId=X` → xem bin nào còn trống\n"
            + "2. `POST /v1/putaway-tasks/{id}/allocate` → đặt chỗ\n"
            + "3. Lặp lại cho đến khi `remainingQty = 0`\n\n"
            + "**Validation:**\n"
            + "- Không được allocate quá số lượng cần cất (`quantity - putawayQty - allocatedQty`)\n"
            + "- Không được allocate quá sức chứa bin (occupied + reserved + qty ≤ maxCapacity)")
    public ApiResponse<List<PutawayAllocationResponse>> allocate(
            @PathVariable Long id,
            @Valid @RequestBody PutawayAllocateRequest request,
            Authentication auth) {
        return putawayTaskService.allocate(id, request, extractUserId(auth));
    }

    // ─── Get allocations ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/allocations")
    @Operation(summary = "Xem danh sách phân bổ", description = "Xem tất cả allocations (RESERVED, CONFIRMED, CANCELLED) cho 1 putaway task.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Putaway Task ID.")
    public ApiResponse<List<PutawayAllocationResponse>> getAllocations(@PathVariable Long id) {
        return putawayTaskService.getAllocations(id);
    }

    // ─── Cancel allocation ───────────────────────────────────────────────────────

    @DeleteMapping("/{id}/allocations/{allocationId}")
    @Operation(summary = "Hủy 1 phân bổ", description = "Hủy 1 allocation RESERVED → giải phóng capacity bin.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Putaway Task ID.\n"
            + "- `@PathVariable allocationId`: ID phân bổ cần hủy.")
    public ApiResponse<Void> cancelAllocation(
            @PathVariable Long id,
            @PathVariable Long allocationId) {
        return putawayTaskService.cancelAllocation(id, allocationId);
    }

    // ─── Confirm all ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận cất hàng (Confirm all)", description = "Confirm tất cả allocations đã RESERVED:\n"
            + "- Di chuyển inventory từ staging → bin đích\n"
            + "- Ghi transaction PUTAWAY\n"
            + "- Auto-complete task nếu tất cả items done\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Putaway Task ID.\n"
            + "- Không cần body request.")
    public ApiResponse<PutawayTaskResponse> confirm(
            @PathVariable Long id,
            Authentication auth) {
        return putawayTaskService.confirmAll(id, extractUserId(auth));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object raw = ((Map<?, ?>) auth.getDetails()).get("warehouseIds");
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long) return (Long) first;
                if (first instanceof Integer) return ((Integer) first).longValue();
                if (first instanceof Number) return ((Number) first).longValue();
                if (first != null) {
                    try { return Long.parseLong(first.toString()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        // warehouseIds empty or missing — return null, service will fetch all tasks
        return null;
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