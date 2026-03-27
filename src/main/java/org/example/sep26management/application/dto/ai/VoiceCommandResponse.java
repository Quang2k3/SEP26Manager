package org.example.sep26management.application.dto.ai;

import lombok.*;

/**
 * Kết quả phân tích lệnh giọng nói từ AI.
 *
 * action:
 *   NAVIGATE  → FE tự động router.push(route)
 *   SHOW_INFO → FE hiện toast thông tin
 *   UNKNOWN   → FE báo không hiểu lệnh
 *   ERROR     → lỗi hệ thống
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceCommandResponse {

    private String action;   // NAVIGATE | SHOW_INFO | UNKNOWN | ERROR
    private String route;    // null nếu action != NAVIGATE
    private String message;  // Câu trả lời tiếng Việt hiển thị cho user
    private Object data;     // Thông tin thêm (optional)
}