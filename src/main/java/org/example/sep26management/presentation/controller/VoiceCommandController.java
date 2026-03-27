package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.ai.VoiceCommandRequest;
import org.example.sep26management.application.dto.ai.VoiceCommandResponse;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.service.VoiceCommandService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * VoiceCommandController
 * POST /v1/ai/voice-command
 *
 * Nhận lệnh tiếng Việt (text) từ FE → gọi VoiceCommandService → trả action + route
 */
@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Voice", description = "Điều khiển WMS bằng giọng nói tiếng Việt")
public class VoiceCommandController {

    private final VoiceCommandService voiceCommandService;

    @PostMapping("/voice-command")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Phân tích lệnh giọng nói tiếng Việt và trả về hành động điều hướng")
    public ApiResponse<VoiceCommandResponse> processVoiceCommand(
            @Valid @RequestBody VoiceCommandRequest request) {

        VoiceCommandResponse result = voiceCommandService.processCommand(request.getText());
        return ApiResponse.success("Xử lý lệnh thành công", result);
    }
}