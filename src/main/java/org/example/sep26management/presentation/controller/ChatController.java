package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.chat.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Hệ thống tin nhắn nội bộ realtime")
public class ChatController {

    private final ChatService chatService;

    // ─── REST endpoints ────────────────────────────────────────────────────────

    @GetMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách phòng chat của user")
    public ApiResponse<List<ChatRoomDto>> listRooms(Authentication auth) {
        return chatService.listRooms(extractUserId(auth));
    }

    @PostMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo hoặc lấy phòng chat (DIRECT/ORDER)")
    public ApiResponse<ChatRoomDto> getOrCreateRoom(
            @RequestBody CreateRoomRequest req,
            Authentication auth) {
        return chatService.getOrCreateRoom(extractUserId(auth), req);
    }

    @GetMapping("/rooms/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy lịch sử tin nhắn (phân trang)")
    public ApiResponse<List<ChatMessageDto>> getMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication auth) {
        return chatService.getMessages(roomId, extractUserId(auth), page, size);
    }

    @PostMapping("/rooms/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi tin nhắn (REST fallback)")
    public ApiResponse<ChatMessageDto> sendMessage(
            @PathVariable Long roomId,
            @RequestBody SendMessageRequest req,
            Authentication auth) {
        ChatMessageDto msg = chatService.sendMessage(roomId, extractUserId(auth), req.getContent());
        return ApiResponse.success("Sent", msg);
    }

    @PatchMapping("/rooms/{roomId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc tất cả tin trong phòng")
    public ApiResponse<Void> markRead(@PathVariable Long roomId, Authentication auth) {
        return chatService.markRead(roomId, extractUserId(auth));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tổng số tin chưa đọc")
    public ApiResponse<Long> unreadCount(Authentication auth) {
        return chatService.getTotalUnread(extractUserId(auth));
    }

    @GetMapping("/members")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách thành viên nội bộ để nhắn tin (mọi role đều truy cập được)")
    public ApiResponse<List<ChatMemberDto>> listChatMembers(
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return chatService.listChatMembers(extractUserId(auth), keyword);
    }

    // ─── WebSocket message handlers ───────────────────────────────────────────

    /** Client gửi: /app/chat/{roomId}/send */
    @MessageMapping("/chat/{roomId}/send")
    public void handleMessage(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequest req,
            java.security.Principal principal) {
        Long userId = extractUserIdFromPrincipal(principal);
        if (userId == null) {
            log.warn("[WS] handleMessage: cannot extract userId, principal={}", principal);
            return;
        }
        chatService.sendMessage(roomId, userId, req.getContent());
    }

    /** Client gửi: /app/chat/{roomId}/typing */
    @MessageMapping("/chat/{roomId}/typing")
    public void handleTyping(
            @DestinationVariable Long roomId,
            @Payload Map<String, Object> payload,
            java.security.Principal principal) {
        Long userId = extractUserIdFromPrincipal(principal);
        if (userId == null) return;
        boolean typing = Boolean.TRUE.equals(payload.get("typing"));
        chatService.broadcastTyping(roomId, userId, typing);
    }

    /** Client gửi: /app/chat/{roomId}/read */
    @MessageMapping("/chat/{roomId}/read")
    public void handleRead(
            @DestinationVariable Long roomId,
            java.security.Principal principal) {
        Long userId = extractUserIdFromPrincipal(principal);
        if (userId == null) return;
        chatService.markRead(roomId, userId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof java.util.Map<?,?> map) {
            Object uid = map.get("userId");
            if (uid instanceof Long l) return l;
            if (uid instanceof Integer i) return i.longValue();
        }
        throw new RuntimeException("Cannot extract userId");
    }

    private Long extractUserIdFromPrincipal(java.security.Principal principal) {
        try {
            if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token) {
                if (token.getDetails() instanceof java.util.Map<?,?> map) {
                    Object uid = map.get("userId");
                    if (uid instanceof Long l) return l;
                    if (uid instanceof Integer i) return i.longValue();
                    if (uid != null) return Long.parseLong(uid.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Cannot extract userId from Principal: {}", e.getMessage());
        }
        return null;
    }
}