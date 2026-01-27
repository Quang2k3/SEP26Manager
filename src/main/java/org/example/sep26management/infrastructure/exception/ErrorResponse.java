package org.example.sep26management.infrastructure.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Boolean success;
    private String message;
    private String error;
    private Integer status;
    private String path;
    private LocalDateTime timestamp;
    private Map<String, String> validationErrors;

    public static ErrorResponse of(String message, Integer status, String path) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .status(status)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
}