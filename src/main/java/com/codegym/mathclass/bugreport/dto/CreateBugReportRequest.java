package com.codegym.mathclass.bugreport.dto;

import com.codegym.mathclass.bugreport.entity.BugErrorType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBugReportRequest {

    @Email(message = "Email không đúng định dạng")
    private String reporterEmail;

    private String reporterName;

    @NotNull(message = "Loại lỗi không được để trống")
    private BugErrorType errorType;

    private String description;

    @Size(max = 3, message = "Chỉ được gửi tối đa 3 ảnh đính kèm")
    private List<String> imageUrls;
}
