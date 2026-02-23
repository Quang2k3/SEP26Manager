package org.example.sep26management.application.dto.response;

import lombok.*;
import org.example.sep26management.application.dto.scan.ScanLineItem;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionResponse {

    private String sessionId;
    private Long warehouseId;
    private String scanToken;
    /** URL the iPhone should open (contains the scan token) */
    private String scanUrl;
    private List<ScanLineItem> lines;
}
