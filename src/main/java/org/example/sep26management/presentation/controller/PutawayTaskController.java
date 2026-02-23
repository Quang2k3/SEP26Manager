package org.example.sep26management.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.PutawayConfirmRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.application.service.PutawayTaskService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/putaway-tasks")
@RequiredArgsConstructor
public class PutawayTaskController {

    private final PutawayTaskService putawayTaskService;

    /**
     * GET /v1/putaway-tasks?assignedTo=me&status=OPEN
     * Keeper fetches their task list.
     */
    @GetMapping
    public ApiResponse<List<PutawayTaskResponse>> list(
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) String status) {
        return putawayTaskService.listTasks(assignedTo, status);
    }

    /** GET /v1/putaway-tasks/{id} — detail with items */
    @GetMapping("/{id}")
    public ApiResponse<PutawayTaskResponse> get(@PathVariable Long id) {
        return putawayTaskService.getTask(id);
    }

    /**
     * POST /v1/putaway-tasks/{id}/confirm
     * Keeper scans shelf and assigns actual location + qty for each item.
     */
    @PostMapping("/{id}/confirm")
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
