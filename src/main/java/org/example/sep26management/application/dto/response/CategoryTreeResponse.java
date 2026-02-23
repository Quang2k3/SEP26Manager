package org.example.sep26management.application.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTreeResponse {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Boolean active;
    private String mappedZoneCode;   // Convention: "Z-" + categoryCode
    private Boolean zoneMapped;      // zone tồn tại hay chưa
    private List<CategoryTreeResponse> children;
}