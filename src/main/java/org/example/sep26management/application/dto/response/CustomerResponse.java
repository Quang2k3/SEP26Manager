package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {

    private Long customerId;
    private String customerCode;
    private String customerName;
    private String email;
    private String phone;
    private Boolean active;
}