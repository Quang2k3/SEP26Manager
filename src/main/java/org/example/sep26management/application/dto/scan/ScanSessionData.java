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
    private Long warehouseId;
    private Long createdBy;

    @Builder.Default
    private List<ScanLineItem> lines = new ArrayList<>();
}
