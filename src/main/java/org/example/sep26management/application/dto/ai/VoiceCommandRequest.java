package org.example.sep26management.application.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoiceCommandRequest {

    @NotBlank(message = "Lệnh không được để trống")
    @Size(max = 500, message = "Lệnh quá dài (tối đa 500 ký tự)")
    private String text;
}