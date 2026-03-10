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
    @Operation(summary = "Chi tiết putaway task", description = "Bóc tách xem bên trong Task này yêu cầu bốc những món hàng nào đi cất.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã **Putaway Task ID**, lấy từ API danh sách `/v1/putaway-tasks` phía trên.\n\n"
            + "*(Ghi chú: Response có sẵn luôn `suggestedLocationCode` để FE biết luôn món hàng này hệ thống AI khuyên cất vào kệ nào)*.")
    public ApiResponse<PutawayTaskResponse> get(@PathVariable Long id) {
        return putawayTaskService.getTask(id);
    }

    /** GET /v1/putaway-tasks/receiving/{receivingId} — get by receiving GRN */
    @GetMapping("/receiving/{receivingId}")
    @Operation(summary = "Lấy putaway task theo phiếu nhập", description = "Lấy Putaway Task được sinh ra tự động từ phiếu nhập GRN.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable receivingId`: Mã phiếu nhập GRN ID.")
    public ApiResponse<PutawayTaskResponse> getByReceivingId(@PathVariable Long receivingId) {
        return putawayTaskService.getTaskByReceivingId(receivingId);
    }

    /**
     * GET /v1/putaway-tasks/{id}/suggestions
     * Get putaway suggestions for all items in a task (zone-category matching).
     */
    @GetMapping("/{id}/suggestions")
    @Operation(summary = "Xem Gợi ý Vị trí Cất Hàng", description = "Nếu FE muốn xem màn hình chi tiết gợi ý AI của riêng lô hàng này, thì gọi API này.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Vẫn là **Putaway Task ID**. \n\n"
            + "**Kết quả:** Tách riêng danh sách AI Suggestions: `matchedZoneCode`, `suggestedLocationCode`, sức chứa tối đa, chỗ trống còn lại trên kệ để thủ kho quyết định có nghe theo gợi ý hay không.")
    public ApiResponse<List<PutawaySuggestion>> getSuggestions(@PathVariable Long id) {
        return putawayTaskService.getSuggestions(id);
    }

    /**
     * POST /v1/putaway-tasks/{id}/confirm
     * Keeper scans shelf and assigns actual location + qty for each item.
     */
    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận cất hàng lên Kệ (Keeper)", description = "Sau khi khuân đồ ra kệ, keeper lấy súng quét mã vạch trên kệ để chốt lại việc dọn đồ.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã **Putaway Task ID**.\n"
            + "- `Body.items`: Là một mảng (List) do FE ghép truyền lên. Mỗi item chứa:\n"
            + "  - `skuId`: Lấy từ response xem ID sản phẩm đã hướng dẫn ở trên.\n"
            + "  - `putawayQty`: Số lượng thực tế đã ném lên kệ (thường thủ kho nhập số trên điện thoại).\n"
            + "  - `locationId`: Ở đâu ra? Do nhân viên kho **cầm máy quét bắn vào cái tem mã vạch dán trên tủ kệ thép**. Sau đó FE truyền mã số của kệ tủ đó vào đây.")
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
