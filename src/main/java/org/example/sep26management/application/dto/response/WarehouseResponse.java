package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WarehouseResponse {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String address;
    private Boolean active;
}