package org.example.sep26management.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.ai.VoiceCommandResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * VoiceCommandService
 * ─────────────────────────────────────────────────────────────────────────────
 * Nhận text tiếng Việt (từ giọng nói), gửi lên Google Gemini (hoặc OpenAI),
 * phân tích ý định → trả về action + route + message tiếng Việt.
 *
 * Cấu hình application.properties (hoặc .env):
 *   ai.provider=gemini                         # gemini | openai
 *   ai.gemini.api-key=AIza...
 *   ai.openai.api-key=sk-...                   # nếu dùng OpenAI
 *
 * Dependency cần thêm vào pom.xml:
 *   (Không cần thêm gì — chỉ dùng RestTemplate có sẵn của Spring Web)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceCommandService {

    @Value("${ai.provider:gemini}")
    private String aiProvider;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── System prompt – định nghĩa các route của WMS ────────────────────────
    private static final String SYSTEM_PROMPT = """
        Bạn là trợ lý AI của hệ thống Quản lý Kho (WMS). Nhiệm vụ của bạn là phân tích
        lệnh tiếng Việt của người dùng và trả về JSON chính xác để điều hướng ứng dụng.

        DANH SÁCH ROUTE HỢP LỆ:
        - /dashboard              → Dashboard tổng quan
        - /inbound/gate-check     → Nhập kho / Gate-Check
        - /outbound               → Xuất kho
        - /tasks                  → Putaway tasks
        - /bin/occupancy          → Bin Occupancy (tình trạng kệ)
        - /bin/search             → Tìm bin trống
        - /bin/floor-plan         → Sơ đồ kho
        - /sku                    → Quản lý SKU / sản phẩm
        - /zone                   → Quản lý Zone / kho hàng
        - /location               → Quản lý vị trí (Location)
        - /location/aisle         → Quản lý Aisle (dãy)
        - /location/rack          → Quản lý Rack (kệ)
        - /location/bin           → Quản lý Bin
        - /category               → Danh mục sản phẩm
        - /manager-dashboard/grn  → Duyệt nhập kho (GRN) - chỉ Manager
        - /manager-dashboard/supplier → Nhà cung cấp - chỉ Manager
        - /manager-dashboard/incident → Sự cố - Manager
        - /incidents              → Sự cố - Keeper/QC
        - /qc-inspections         → QC Inspection
        - /user-management        → Quản lý người dùng - chỉ Manager
        - /profile                → Hồ sơ cá nhân

        YÊU CẦU PHẢN HỒI:
        Luôn trả về JSON hợp lệ theo format sau (không có markdown, không có text thừa):
        {
          "action": "NAVIGATE" | "SHOW_INFO" | "UNKNOWN",
          "route": "/route-nếu-NAVIGATE-hoặc-null",
          "message": "Câu trả lời ngắn bằng tiếng Việt cho user (tối đa 20 từ)"
        }

        VÍ DỤ:
        Input: "mở dashboard"
        Output: {"action":"NAVIGATE","route":"/dashboard","message":"Đang chuyển đến Dashboard"}

        Input: "đi đến nhập kho"
        Output: {"action":"NAVIGATE","route":"/inbound/gate-check","message":"Đang mở trang Nhập kho"}

        Input: "xem bin trống"
        Output: {"action":"NAVIGATE","route":"/bin/search","message":"Đang tìm kiếm Bin trống"}

        Input: "thời tiết hôm nay"
        Output: {"action":"UNKNOWN","route":null,"message":"Tôi chỉ hỗ trợ điều hướng trong hệ thống kho"}

        Input: "đăng xuất"
        Output: {"action":"SHOW_INFO","route":null,"message":"Vui lòng nhấn nút Đăng xuất ở góc phải màn hình"}

        QUAN TRỌNG:
        - Hiểu các từ đồng nghĩa: "mở", "vào", "đi đến", "chuyển sang", "xem", "hiển thị"
        - Hiểu tắt: "GRN" = duyệt nhập kho, "putaway" = tasks, "SKU" = sản phẩm
        - message phải ngắn, thân thiện, tiếng Việt tự nhiên
        - Chỉ trả về JSON thuần, không có ```json hay text bổ sung
        """;

    // ─── Public API ──────────────────────────────────────────────────────────

    public VoiceCommandResponse processCommand(String userText) {
        log.info("[VoiceAI] Xử lý lệnh: '{}'", userText);
        try {
            String jsonResponse = "gemini".equalsIgnoreCase(aiProvider)
                    ? callGemini(userText)
                    : callOpenAI(userText);

            return parseAiResponse(jsonResponse);

        } catch (Exception e) {
            log.error("[VoiceAI] Lỗi xử lý lệnh: {}", e.getMessage(), e);
            return VoiceCommandResponse.builder()
                    .action("ERROR")
                    .message("Xin lỗi, hệ thống AI tạm thời không khả dụng.")
                    .build();
        }
    }

    // ─── Gemini ──────────────────────────────────────────────────────────────

    private String callGemini(String userText) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.0-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userText)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", 200
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.at("/candidates/0/content/parts/0/text").asText();
    }

    // ─── OpenAI (fallback) ───────────────────────────────────────────────────

    private String callOpenAI(String userText) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "temperature", 0.1,
                "max_tokens", 200,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userText)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.at("/choices/0/message/content").asText();
    }

    // ─── Parse JSON từ AI ────────────────────────────────────────────────────

    private VoiceCommandResponse parseAiResponse(String rawJson) {
        try {
            // Loại bỏ markdown nếu AI trả về ```json ... ```
            String cleaned = rawJson
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode node = objectMapper.readTree(cleaned);
            String action = node.path("action").asText("UNKNOWN");
            String route  = node.path("route").isNull() ? null : node.path("route").asText(null);
            String msg    = node.path("message").asText("Không thể phân tích lệnh");

            return VoiceCommandResponse.builder()
                    .action(action)
                    .route(route)
                    .message(msg)
                    .build();

        } catch (Exception e) {
            log.warn("[VoiceAI] Không parse được JSON từ AI: {}", rawJson);
            return VoiceCommandResponse.builder()
                    .action("UNKNOWN")
                    .message("Tôi không hiểu lệnh này. Hãy thử lại.")
                    .build();
        }
    }
}