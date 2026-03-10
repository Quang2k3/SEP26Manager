package org.example.sep26management.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

/**
 * Suggestion detail cho mỗi putaway item.
 * Gồm zone matching info + bin capacity info.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Gợi ý vị trí cất hàng (AI Suggestion) cho 1 SKU. "
        + "Được tính tự động từ zone-category matching: SKU → Category → Zone → Bin có sức chứa lớn nhất.")
public class PutawaySuggestion {

    // ─── SKU Info ────────────────────────────────────────────────────────────────

    @Schema(
            description = "ID sản phẩm (SKU). Lấy từ bảng `skus.sku_id`, "
                    + "được ánh xạ qua `putaway_task_items.sku_id` khi tạo task.",
            example = "20"
    )
    private Long skuId;

    @Schema(
            description = "Mã SKU. Lấy từ `skus.sku_code` của SKU tương ứng.",
            example = "SKU-IPHONE15"
    )
    private String skuCode;

    @Schema(
            description = "Mã danh mục của SKU. Lấy từ `categories.category_code` "
                    + "qua quan hệ `skus.category_id → categories`. "
                    + "Dùng để ghép thành zone: zoneCode = \"Z-\" + categoryCode.",
            example = "ELEC"
    )
    private String categoryCode;

    // ─── Zone Match Info ─────────────────────────────────────────────────────────

    @Schema(
            description = "Mã Zone được hệ thống AI khớp. "
                    + "Quy tắc: zoneCode = \"Z-\" + categoryCode. "
                    + "Lấy từ `zones.zone_code` trong cùng warehouse.",
            example = "Z-ELEC"
    )
    private String matchedZoneCode;

    @Schema(
            description = "ID của Zone được khớp. Lấy từ `zones.zone_id`.",
            example = "5"
    )
    private Long matchedZoneId;

    @Schema(
            description = "Tên đầy đủ của Zone được khớp. Lấy từ `zones.zone_name`.",
            example = "Zone Điện Tử"
    )
    private String matchedZoneName;

    // ─── Suggested Bin Info ───────────────────────────────────────────────────────

    @Schema(
            description = "ID Bin được gợi ý. Lấy từ `locations.location_id`. "
                    + "Đây là Bin trong Zone khớp có `availableCapacity` lớn nhất và đủ chứa lô hàng.",
            example = "42"
    )
    private Long suggestedLocationId;

    @Schema(
            description = "Mã Bin được gợi ý. Lấy từ `locations.location_code`. "
                    + "FE dùng mã này để hiển thị cho thủ kho biết nên cất hàng vào kệ nào.",
            example = "BIN-A01-R01-01"
    )
    private String suggestedLocationCode;

    @Schema(
            description = "Mã Hàng (Aisle) cha của Bin. "
                    + "Lấy từ `locations.location_code` của node cấp Aisle "
                    + "(cha của Rack, ông của Bin) qua `locations.parent_location_id`.",
            example = "AISLE-A01"
    )
    private String aisleName;

    @Schema(
            description = "Mã Kệ (Rack) cha trực tiếp của Bin. "
                    + "Lấy từ `locations.location_code` của node cấp Rack "
                    + "qua `locations.parent_location_id`.",
            example = "RACK-R01"
    )
    private String rackName;

    // ─── Capacity Info ────────────────────────────────────────────────────────────

    @Schema(
            description = "Số lượng hàng đang tồn kho HIỆN TẠI tại Bin gợi ý. "
                    + "Được tính bằng SUM(inventory_transactions.qty) hoặc từ bảng `bin_stock` "
                    + "cho `location_id` tương ứng.",
            example = "15.00"
    )
    private BigDecimal currentQty;

    @Schema(
            description = "Sức chứa tối đa của Bin gợi ý. Lấy từ `locations.max_weight_kg`. "
                    + "Nếu chưa cấu hình, mặc định hệ thống gán 999999.",
            example = "100.00"
    )
    private BigDecimal maxCapacity;

    @Schema(
            description = "Sức chứa còn trống = maxCapacity - currentQty. "
                    + "FE dùng để hiển thị progress bar / cảnh báo sắp đầy kệ.",
            example = "85.00"
    )
    private BigDecimal availableCapacity;

    // ─── Matching Reason ─────────────────────────────────────────────────────────

    @Schema(
            description = "Lý do AI chọn Bin này. Sinh tự động bởi PutawaySuggestionService, "
                    + "không lưu DB. Format: \"Zone {zoneCode} matched category {categoryCode}\".",
            example = "Zone Z-ELEC matched category ELEC"
    )
    private String reason;
}