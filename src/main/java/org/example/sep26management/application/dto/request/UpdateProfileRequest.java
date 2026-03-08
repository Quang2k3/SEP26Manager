package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Schema(description = "Họ và tên người dùng", example = "Nguyễn Văn A")
    @NotBlank(message = "Full name cannot be empty")
    @Size(max = 200, message = "Full name cannot exceed 200 characters")
    private String fullName;

    @Schema(description = "Số điện thoại liên lạc", example = "0987654321")
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{9,15}$", message = "Phone number must be between 9 and 15 digits")
    private String phone;

    @Schema(description = "Giới tính", example = "MALE", allowableValues = { "MALE", "FEMALE", "OTHER" })
    private String gender; // MALE, FEMALE, OTHER

    @Schema(description = "Ngày sinh (Định dạng yyyy-MM-dd)", example = "2003-03-04")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @Schema(description = "Địa chỉ", example = "8 Tôn Thất Thuyết, Hà Nội")
    private String address;

    @Schema(description = "File ảnh đại diện upload lên", type = "string", format = "binary")
    private MultipartFile avatar;
}