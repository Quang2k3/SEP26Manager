package org.example.sep26management.application.dto.scan;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionData {

    private String sessionId;
    private Long   warehouseId;
    private Long   createdBy;

    /**
     * Phiếu nhận hàng mà session này phục vụ.
     * Set khi Keeper/QC generate QR — scan event sẽ validate receivingId khớp.
     * NULL = session legacy (tạo trước khi có field này).
     */
    private Long   receivingId;

    /**
     * Role của người tạo session: "KEEPER" hoặc "QC".
     * Dùng để validate: Keeper không được submit QC và ngược lại.
     */
    private String role;

    /**
     * QC claim lock — set khi QC đầu tiên bắt đầu scan đơn.
     * Nếu != null, QC khác gửi scan event vào session này sẽ bị từ chối.
     * Chỉ có ý nghĩa khi role = "QC".
     */
    private Long   assignedQcId;

    @Builder.Default
    private List<ScanLineItem> lines = new ArrayList<>();
}